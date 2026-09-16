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
    implementation(libs.gson)

    testImplementation(libs.junit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly(libs.slf4j.simple)
}

// ── The authority leg ────────────────────────────────────────────────────────
// `src/test` is held to the Java 11 floor and drives the server through this
// module's own TinyMCPClient, so it also runs on a Java 11 JVM. That alone
// would be circular, so `src/testOfficial` re-runs the shared conformance suite
// through the official MCP SDK — an independent implementation of the same
// protocol. The SDK has no Java 11 build (every release is class-file 61), so
// this set compiles at 17 and is skipped entirely on an older build JDK.
val java17Available = JavaVersion.current() >= JavaVersion.VERSION_17

if (java17Available) {
    sourceSets {
        create("testOfficial") {
            java.srcDir("src/testOfficial/java")
            compileClasspath += sourceSets["main"].output + sourceSets["test"].output
            runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
        }
    }
    configurations {
        getByName("testOfficialImplementation").extendsFrom(configurations["testImplementation"])
        getByName("testOfficialRuntimeOnly").extendsFrom(configurations["testRuntimeOnly"])
    }
    dependencies {
        "testOfficialImplementation"(libs.mcp.client)
        "testOfficialImplementation"(libs.mcp.json.jackson3)
        "testOfficialImplementation"(libs.jetty.servlet)
    }
    tasks.named<JavaCompile>("compileTestOfficialJava") {
        options.release = 17
    }

    val testOfficial by tasks.registering(Test::class) {
        description = "Runs the conformance suite through the official MCP SDK (needs Java 17+)"
        group = "verification"
        testClassesDirs = sourceSets["testOfficial"].output.classesDirs
        classpath = sourceSets["testOfficial"].runtimeClasspath
        reports.html.outputLocation.set(layout.buildDirectory.dir("reports/testOfficial"))
        reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/testOfficial"))
    }
    tasks.named("check") {
        dependsOn(testOfficial)
    }
} else {
    logger.lifecycle(
        "tiny-mcp-server: skipping the official-SDK conformance leg — it needs Java 17+, " +
            "this build runs on ${JavaVersion.current()}."
    )
}

@Suppress("UNCHECKED_CAST")
val configureMavenCentral = ext["configureMavenCentral"] as (artifactId: String) -> Unit
configureMavenCentral("tiny-mcp-server")
