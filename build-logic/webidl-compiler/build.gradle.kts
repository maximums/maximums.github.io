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
}

tasks.generateGrammarSource {
    // The grammar lives in src/main/antlr/com/cdodi/webidl/parser, so the generated sources land in that package's directory.
    arguments = arguments + listOf("-visitor", "-long-messages", "-package", "com.cdodi.webidl.parser")
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
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
