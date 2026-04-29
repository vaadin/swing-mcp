plugins {
    `java-library`
}

dependencies {
    api(project(":tiny-mcp-server"))

    testImplementation(libs.junit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

@Suppress("UNCHECKED_CAST")
val configureMavenCentral = ext["configureMavenCentral"] as (artifactId: String) -> Unit
configureMavenCentral("swing-mcp-tool-defs")
