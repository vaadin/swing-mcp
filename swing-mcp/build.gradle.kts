plugins {
    `java-library`
}

dependencies {
    implementation(project(":tiny-mcp-server"))
    implementation(libs.slf4j.api)

    testImplementation(libs.slf4j.simple)
    testImplementation(libs.junit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

@Suppress("UNCHECKED_CAST")
val configureMavenCentral = ext["configureMavenCentral"] as (artifactId: String) -> Unit
configureMavenCentral("swing-mcp")
