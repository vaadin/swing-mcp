/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
plugins {
    `java-library`
}

dependencies {
    api(libs.tinymcpserver)

    testImplementation(libs.junit)
    testImplementation(libs.bytebuddy)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

@Suppress("UNCHECKED_CAST")
val configureMavenCentral = ext["configureMavenCentral"] as (artifactId: String) -> Unit
configureMavenCentral("swing-mcp")

// Writes the project version beside SwingTools, which advertises it as
// initialize.serverInfo.version.
val versionResources = layout.buildDirectory.dir("generated/version-resources")
val writeVersionProperties by tasks.registering(WriteProperties::class) {
    destinationFile = versionResources.map { it.file("com/vaadin/swingmcp/tools/version.properties") }
    property("version", project.version.toString())
}

// The expected value for the test that pins serverInfo.version to the build.
tasks.withType<Test> {
    systemProperty("swingmcp.expectedVersion", project.version.toString())
}

sourceSets {
    main {
        resources.srcDir(files(versionResources).builtBy(writeVersionProperties))
    }
    create("testSwing") {
        java.srcDir("src/testSwing/java")
        resources.srcDir("src/testSwing/resources")
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
    }
}
configurations {
    getByName("testSwingImplementation").extendsFrom(configurations["testImplementation"])
    getByName("testSwingRuntimeOnly").extendsFrom(configurations["testRuntimeOnly"])
}

val testSwing by tasks.registering(Test::class) {
    description = "Runs Swing/screen-requiring tests"
    group = "verification"

    testClassesDirs = sourceSets["testSwing"].output.classesDirs
    classpath = sourceSets["testSwing"].runtimeClasspath

    // Explicitly ensure headless is OFF for this task
    systemProperty("java.awt.headless", "false")

    // Optional: isolate reports from the main test task
    reports.html.outputLocation.set(layout.buildDirectory.dir("reports/testSwing"))
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/testSwing"))
}
tasks.named("check") {
    dependsOn(testSwing)
}
tasks.named<Test>("test") {
    systemProperty("java.awt.headless", "true")
}