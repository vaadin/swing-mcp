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
