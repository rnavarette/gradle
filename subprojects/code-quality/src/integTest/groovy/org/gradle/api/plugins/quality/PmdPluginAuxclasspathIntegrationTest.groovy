/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.plugins.quality

import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.VersionNumber
import org.hamcrest.Matcher
import org.junit.Assume

import static org.gradle.util.Matchers.containsLine
import static org.gradle.util.Matchers.containsText
import static org.hamcrest.Matchers.containsString

@LeaksFileHandles
class PmdPluginAuxclasspathIntegrationTest extends AbstractPmdPluginVersionIntegrationTest {

    static boolean supportsAuxclasspath() {
        return VersionNumber.parse("5.2.0") < versionNumber
    }

    def setup() {
        settingsFile << 'include "pmd-rule", "rule-using"'

        buildFile << """
            allprojects {
                repositories {
                    mavenCentral()
                }
                apply plugin: 'java'
            }

            project("pmd-rule") {
                dependencies {
                    compile "${calculateDefaultDependencyNotation()}"
                }
            }

            project("rule-using") {
                apply plugin: 'pmd'

                dependencies {
                    compile "junit:junit:3.8.1"

                    pmd project(":pmd-rule")
                }

                pmd {
                    ruleSets = ["java-auxclasspath"]
                }
            }
        """

        file("pmd-rule/src/main/resources/rulesets/java/auxclasspath.xml") << rulesetXml()
        file("pmd-rule/src/main/java/org/gradle/pmd/rules/AuxclasspathRule.java") << ruleCode()

        file("rule-using/src/main/java/org/gradle/ruleusing/Class1.java") << analyzedCode()
    }

    def "auxclasspath configured for rule-using project"() {
        Assume.assumeTrue(supportsAuxclasspath())

        expect:
        fails ":rule-using:pmdMain"

        file("rule-using/build/reports/pmd/main.xml").
            assertContents(containsClass("org.gradle.ruleusing.Class1")).
            assertContents(containsText("auxclasspath configured"))
    }

    def "auxclasspath not configured properly for rule-using project"() {
        Assume.assumeTrue(supportsAuxclasspath())

        given:
        buildFile << """
project("rule-using") {
    tasks.withType(Pmd) {
        // clear the classpath
        classpath = files()
    }
}
"""
        expect:
        fails ":rule-using:pmdMain"

        file("rule-using/build/reports/pmd/main.xml").
            assertContents(containsClass("org.gradle.ruleusing.Class1")).
            assertContents(containsText("auxclasspath not configured"))
    }

    private static Matcher<String> containsClass(String className) {
        containsLine(containsString(className.replace(".", File.separator)))
    }

    private ruleCode() {
        """
            package org.gradle.pmd.rules;

            import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
            import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;

            public class AuxclasspathRule extends AbstractJavaRule {

                private static final String JUNIT_TEST = "junit.framework.TestCase";

                @Override
                public Object visit(final ASTCompilationUnit node, final Object data) {
                    if (node.getClassTypeResolver().classNameExists(JUNIT_TEST)) {
                        addViolationWithMessage(data, node, "auxclasspath configured.");
                    } else {
                        addViolationWithMessage(data, node, "auxclasspath not configured.");
                    }
                    return super.visit(node, data);
                }
            }
        """
    }

    private rulesetXml() {
        """
            <ruleset name="auxclasspath"
                xmlns="http://pmd.sf.net/ruleset/2.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://pmd.sf.net/ruleset/2.0.0 http://pmd.sf.net/ruleset_2_0_0.xsd"
                xsi:noNamespaceSchemaLocation="http://pmd.sf.net/ruleset_2_0_0.xsd">

                <rule name="Auxclasspath"
                    class="org.gradle.pmd.rules.AuxclasspathRule"
                    typeResolution="true">
                </rule>
            </ruleset>
        """
    }

    private analyzedCode() {
        """
            package org.gradle.ruleusing;
            public class Class1 extends junit.framework.TestCase { }
        """
    }
}
