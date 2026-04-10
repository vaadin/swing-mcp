plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.6"
}

dependencies {
    implementation(project(":swing-mcp"))
    implementation(libs.slf4j.api)

    testImplementation(libs.slf4j.simple)
    testImplementation(libs.junit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    manifest {
        attributes(
            "Premain-Class" to "com.vaadin.swingmcp.agent.Agent",
            "Can-Retransform-Classes" to "false",
            "Can-Redefine-Classes" to "false",
        )
    }
}

tasks.shadowJar {
    archiveClassifier.set("")    // produce swing-mcp-agent-<version>.jar (no -all suffix)
    mergeServiceFiles()          // merge META-INF/services/* from all dependencies
}

// Make 'build' produce the shadow jar instead of the thin jar
tasks.named("build") {
    dependsOn(tasks.shadowJar)
}

@Suppress("UNCHECKED_CAST")
val configureMavenCentral = ext["configureMavenCentral"] as (artifactId: String) -> Unit
configureMavenCentral("swing-mcp-agent")
