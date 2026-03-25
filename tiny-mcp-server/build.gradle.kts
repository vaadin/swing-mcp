plugins {
    `java-library`
}

dependencies {
    implementation(libs.slf4j.api)
    implementation(libs.gson)

    testImplementation(libs.mcp.client)
    testImplementation(libs.slf4j.simple)
    testImplementation(libs.junit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

@Suppress("UNCHECKED_CAST")
val configureMavenCentral = ext["configureMavenCentral"] as (artifactId: String) -> Unit
configureMavenCentral("tiny-mcp-server")
