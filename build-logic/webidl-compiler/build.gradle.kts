import java.net.URI

plugins {
    `kotlin-dsl`
    antlr
}

dependencies {
    antlr(libs.antlr)
    implementation(libs.antlr.runtime)
    implementation(libs.kotlinpoet)
    compileOnly(libs.kotlin.gradle.plugin)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// TestKit loads the plugin under test in its own classloader, which must also see the Kotlin Gradle plugin it builds on.
val testKitKotlinPlugin by configurations.creating
dependencies { testKitKotlinPlugin(libs.kotlin.gradle.plugin) }
tasks.pluginUnderTestMetadata {
    pluginClasspath.from(testKitKotlinPlugin)
}

tasks.generateGrammarSource {
    // The grammar lives in src/main/antlr/com/cdodi/webidl/parser, so the generated sources land in that package's directory.
    arguments = arguments + listOf("-visitor", "-long-messages", "-package", "com.cdodi.webidl.parser")
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

/** `-PupdateGoldens` rewrites src/test/golden from the current compiler output instead of checking against it. */
val updateGoldens = providers.gradleProperty("updateGoldens").isPresent
tasks.test {
    useJUnitPlatform()

    val fixtures = layout.projectDirectory.dir("src/test/fixtures")
    val golden = layout.projectDirectory.dir("src/test/golden")
    inputs.dir(fixtures).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("fixtures")
    inputs.files(golden).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("golden")
    systemProperty("webidl.fixtures", fixtures.asFile.absolutePath)
    systemProperty("webidl.golden", golden.asFile.absolutePath)
    systemProperty("webidl.updateGoldens", updateGoldens)

    // CompileTest compiles the generated code against the real runtime helpers of :webgpu.
    val runtimeSource = layout.projectDirectory.file("../../webgpu/src/wasmJsMain/kotlin/com/cdodi/webgpu/runtime/Runtime.kt")
    inputs.file(runtimeSource).withPathSensitivity(PathSensitivity.NONE).withPropertyName("runtimeSource")
    systemProperty("webidl.runtimeSource", runtimeSource.asFile.absolutePath)
    if (updateGoldens) outputs.upToDateWhen { false }
}

gradlePlugin {
    plugins {
        register("webIdl") {
            id = "com.cdodi.webidl"
            displayName = "WebIDL compiler"
            description = "Generates Kotlin/Wasm declarations from committed WebIDL files"
            implementationClass = "com.cdodi.webidl.gradle.WebIdlPlugin"
        }
    }
}

/**
 * Run by hand: `./gradlew -p build-logic :webidl-compiler:updateWebIdlGrammar -PgrammarRef=<grammars-v4 commit>`.
 * Pins the ANTLR WebIDL grammar to a commit of antlr/grammars-v4 and overwrites the committed copy.
 */
val grammarFile = layout.projectDirectory.file("src/main/antlr/com/cdodi/webidl/parser/WebIDL.g4")
val grammarRef = providers.gradleProperty("grammarRef")
tasks.register("updateWebIdlGrammar") {
    group = "webidl"
    description = "Run by hand: pins WebIDL.g4 to a grammars-v4 commit (-PgrammarRef=<sha>)."
    doNotTrackState("Overwrites a committed source on request")
    val target = grammarFile.asFile
    val ref = grammarRef
    doLast {
        val sha = ref.orNull ?: throw GradleException("Pass the grammars-v4 commit to pin: -PgrammarRef=<sha>")
        val url = "https://raw.githubusercontent.com/antlr/grammars-v4/$sha/webidl/WebIDL.g4"
        val grammar = URI.create(url).toURL().readText()
        target.writeText("// Source: antlr/grammars-v4@$sha webidl/WebIDL.g4 — updated by updateWebIdlGrammar\n$grammar")
        logger.lifecycle("WebIDL.g4 pinned to grammars-v4@$sha. Review the diff, rebuild and commit.")
    }
}
