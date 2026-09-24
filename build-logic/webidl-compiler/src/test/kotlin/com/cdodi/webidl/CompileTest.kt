package com.cdodi.webidl

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Applies the plugin to a Kotlin/Wasm build with one subproject per fixture and compiles the generated code.
 * The goldens prove what is generated; this proves that it compiles.
 */
class CompileTest {

    private val fixturesDir = File(System.getProperty("webidl.fixtures"))
    private val runtimeSource = File(System.getProperty("webidl.runtimeSource"))

    @TempDir
    lateinit var projectDir: File

    @Test
    fun generatedCodeCompiles() {
        val fixtures = fixturesDir.listFiles { file -> file.extension == "idl" }.orEmpty().map(File::nameWithoutExtension).sorted()
        writeProject(fixtures)

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(fixtures.map { ":$it:compileKotlinWasmJs" } + listOf("--continue", "--stacktrace"))
            .run { if (KNOWN_NOT_COMPILING.isEmpty()) build() else buildAndFail() }

        val outcomes = fixtures.associateWith { result.task(":$it:compileKotlinWasmJs")?.outcome }
        val expected = fixtures.associateWith { if (it in KNOWN_NOT_COMPILING) TaskOutcome.FAILED else TaskOutcome.SUCCESS }

        assertEquals(expected, outcomes, "Compile outcome per fixture (update KNOWN_NOT_COMPILING when a fix lands)\n${result.output.takeLast(6000)}")
    }

    private fun writeProject(fixtures: List<String>) {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            dependencyResolutionManagement {
                repositories { mavenCentral() }
            }
            include(":runtime")
            ${fixtures.joinToString("\n") { "include(\":$it\")" }}
            """.trimIndent()
        )
        projectDir.resolve("gradle.properties").writeText("org.gradle.parallel=true\nkotlin.daemon.jvmargs=-Xmx1g\n")

        fun kotlinBlock(dependencies: String = "") = """
            kotlin {
                wasmJs { browser() }
                compilerOptions { optIn.add("kotlin.js.ExperimentalWasmJsInterop") }
                sourceSets { wasmJsMain.dependencies { $dependencies } }
            }
        """.trimIndent()

        projectDir.resolve("runtime").apply {
            resolve("build.gradle.kts").also { it.parentFile.mkdirs() }.writeText(
                "plugins { kotlin(\"multiplatform\") }\n${kotlinBlock()}\n"
            )
            resolve("src/wasmJsMain/kotlin/Runtime.kt").also { it.parentFile.mkdirs() }.writeText(runtimeSource.readText())
        }

        for (fixture in fixtures) {
            projectDir.resolve(fixture).apply {
                resolve("fixture.idl").also { it.parentFile.mkdirs() }.writeText(fixturesDir.resolve("$fixture.idl").readText())
                resolve("build.gradle.kts").writeText(
                    """
                    plugins {
                        kotlin("multiplatform")
                        id("com.cdodi.webidl")
                    }
                    ${kotlinBlock("implementation(project(\":runtime\"))")}
                    webIdl {
                        idlFiles.from("fixture.idl")
                        packageName = "fixtures.${fixture.replace('-', '_')}"
                        runtimePackage = "${GoldenTest.RUNTIME_PACKAGE}"
                    }
                    """.trimIndent()
                )
            }
        }
    }

    private companion object {
        /** Fixtures whose generated code does not compile yet. Each entry is a known bug; remove it with the fix. */
        val KNOWN_NOT_COMPILING = setOf(
            "sequences", // List<Int>.toJsArray(): toJsArray needs JsAny? elements (REVIEW W1, sequence<primitive>)
        )
    }
}
