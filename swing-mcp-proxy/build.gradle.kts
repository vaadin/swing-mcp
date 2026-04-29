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
