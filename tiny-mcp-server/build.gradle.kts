plugins {
    `java-library`
}

dependencies {
    implementation(libs.gson)

    testImplementation(libs.mcp.client)
    testImplementation(libs.mcp.json.jackson3)
    testImplementation(libs.jetty.servlet)
    testImplementation(libs.junit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly(libs.slf4j.simple)
}

@Suppress("UNCHECKED_CAST")
val configureMavenCentral = ext["configureMavenCentral"] as (artifactId: String) -> Unit
configureMavenCentral("tiny-mcp-server")
