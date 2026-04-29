plugins {
    `java-library`
}

dependencies {
    api(project(":swing-mcp-tool-defs"))
    implementation(project(":tiny-mcp-server"))

    testImplementation(libs.mcp.client)
    testImplementation(libs.mcp.json.jackson3)
    testImplementation(libs.junit)
    testImplementation(libs.bytebuddy)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

@Suppress("UNCHECKED_CAST")
val configureMavenCentral = ext["configureMavenCentral"] as (artifactId: String) -> Unit
configureMavenCentral("swing-mcp")

sourceSets {
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