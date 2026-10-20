plugins {
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

/**
 * Colours come from the theme (the design system's `theme` package): a colour written anywhere else (`Color(0x…)`,
 * `Color(r, g, b)`) can't follow a change of theme. The still street's lights are the study's own and move to the
 * theme with the shaders' colours.
 */
tasks.register("checkThemeTokens") {
    group = "verification"
    description = "Fails when a colour is written outside the design system's theme."
    val sources = listOf(
        layout.projectDirectory.dir("composeApp/src/wasmJsMain/kotlin"),
        layout.projectDirectory.dir("design-system/src/wasmJsMain/kotlin"),
    )
    sources.forEachIndexed { i, dir -> inputs.dir(dir).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("sources$i") }
    val roots = sources.map { it.asFile }
    val repository = layout.projectDirectory.asFile
    doLast {
        val allowed = listOf("design-system/src/wasmJsMain/kotlin/com/cdodi/design/theme/")
        val known = setOf("composeApp/src/wasmJsMain/kotlin/com/cdodi/features/background/StillScene.kt")
        val literal = Regex("""\bColor\(\s*(?:0x|\d)""")
        val written = roots.asSequence().flatMap { it.walk() }.filter { it.extension == "kt" }.flatMap { file ->
            val path = file.relativeTo(repository).invariantSeparatorsPath
            if (allowed.any { path.startsWith(it) } || path in known) return@flatMap emptySequence()
            file.readLines().asSequence().withIndex()
                .filter { (_, line) -> literal.containsMatchIn(line) && !line.trimStart().startsWith("*") && !line.trimStart().startsWith("//") }
                .map { (index, line) -> "$path:${index + 1}: ${line.trim()}" }
        }.toList()
        if (written.isNotEmpty()) throw GradleException("Colours written outside the theme:\n" + written.joinToString("\n"))
    }
}
