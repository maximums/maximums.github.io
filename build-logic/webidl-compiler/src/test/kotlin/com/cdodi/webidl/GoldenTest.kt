package com.cdodi.webidl

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Compiles every `src/test/fixtures/<name>.idl` and compares the output with `src/test/golden/<name>/`.
 *
 * `./gradlew -p build-logic :webidl-compiler:test -PupdateGoldens` rewrites the goldens instead;
 * the change to the generated API is then reviewed as an ordinary diff.
 */
class GoldenTest {

    private val fixturesDir = File(System.getProperty("webidl.fixtures"))
    private val goldenDir = File(System.getProperty("webidl.golden"))
    private val updateGoldens = System.getProperty("webidl.updateGoldens").toBoolean()

    @TestFactory
    fun fixtures(): List<DynamicTest> {
        val fixtures = fixturesDir.listFiles { file -> file.extension == "idl" }.orEmpty().sortedBy(File::getName)
        check(fixtures.isNotEmpty()) { "No fixtures in $fixturesDir" }

        return fixtures.map { fixture -> DynamicTest.dynamicTest(fixture.nameWithoutExtension) { check(fixture) } }
    }

    private fun check(fixture: File) {
        val actual = generate(fixture)
        val expectedDir = goldenDir.resolve(fixture.nameWithoutExtension)

        if (updateGoldens) {
            expectedDir.deleteRecursively()
            expectedDir.mkdirs()
            actual.forEach { (name, text) -> expectedDir.resolve(name).writeText(text) }
            return
        }

        val expected = expectedDir.listFiles().orEmpty().associate { it.name to it.readText().normalized() }
        if (expected.isEmpty()) fail<Unit>("No golden files for ${fixture.name}; run with -PupdateGoldens")

        assertEquals(expected.keys.sorted(), actual.keys.sorted(), "Generated files for ${fixture.name}")
        for ((name, text) in actual) {
            val golden = expected.getValue(name)
            if (golden != text) fail<Unit>("${fixture.name} -> $name differs from its golden file:\n${firstDifference(golden, text)}")
        }
    }

    /** File name -> content. A compiler crash is recorded as ERROR.txt, so crashes are pinned down too. */
    private fun generate(fixture: File): Map<String, String> = try {
        WebIdlCompiler.compile(
            sources = listOf(IdlSource(fixture.name, fixture.readText())),
            options = CompilerOptions(
                packageName = "fixtures." + fixture.nameWithoutExtension.replace('-', '_'),
                runtimePackage = RUNTIME_PACKAGE,
            ),
        ).associate { spec -> "${spec.name}.kt" to spec.toString().normalized() }
    } catch (e: Exception) {
        mapOf("ERROR.txt" to "${e::class.simpleName}: ${e.message}\n".normalized())
    }

    private fun String.normalized() = replace("\r\n", "\n")

    private fun firstDifference(expected: String, actual: String): String {
        val expectedLines = expected.lines()
        val actualLines = actual.lines()
        val index = expectedLines.indices.firstOrNull { it >= actualLines.size || expectedLines[it] != actualLines[it] }
            ?: expectedLines.size
        val from = (index - 2).coerceAtLeast(0)

        return buildString {
            appendLine("first difference at line ${index + 1}")
            appendLine("--- golden")
            expectedLines.subList(from, (index + 3).coerceAtMost(expectedLines.size)).forEach { appendLine("  $it") }
            appendLine("+++ generated")
            actualLines.subList(from.coerceAtMost(actualLines.size), (index + 3).coerceAtMost(actualLines.size)).forEach { appendLine("  $it") }
        }
    }

    companion object {
        const val RUNTIME_PACKAGE = "com.cdodi.webgpu.runtime"
    }
}
