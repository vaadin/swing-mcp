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
    id("com.gradleup.shadow") version "8.3.6"
}

dependencies {
    implementation(project(":tiny-mcp-server"))
    implementation(project(":swing-mcp-tool-defs"))

    testImplementation(libs.junit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.vaadin.swingmcp.proxy.Main",
        )
    }
}

tasks.shadowJar {
    archiveClassifier.set("")    // produce swing-mcp-proxy-<version>.jar (no -all suffix)
    mergeServiceFiles()          // merge META-INF/services/* from all dependencies
}

// Make 'build' produce the shadow jar instead of the thin jar
tasks.named("build") {
    dependsOn(tasks.shadowJar)
}

@Suppress("UNCHECKED_CAST")
val configureMavenCentral = ext["configureMavenCentral"] as (artifactId: String) -> Unit
configureMavenCentral("swing-mcp-proxy")
