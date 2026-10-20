plugins {
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

/**
 * Colours come from the theme (the design system's `theme` package): a colour written anywhere else can't follow a
 * change of theme. In Kotlin that's `Color(0x…)` or `Color(r, g, b)`; in a shader, `vec3f(r, g, b) / 255`, where the
 * theme's uniforms (`common/theme.wgsl`) belong.
 */
tasks.register("checkThemeTokens") {
    group = "verification"
    description = "Fails when a colour is written outside the design system's theme."
    val sources = listOf(
        layout.projectDirectory.dir("composeApp/src/wasmJsMain/kotlin"),
        layout.projectDirectory.dir("design-system/src/wasmJsMain/kotlin"),
        layout.projectDirectory.dir("composeApp/src/commonMain/composeResources/files/shaders"),
    )
    sources.forEachIndexed { i, dir -> inputs.dir(dir).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("sources$i") }
    val roots = sources.map { it.asFile }
    val repository = layout.projectDirectory.asFile
    doLast {
        val theme = "design-system/src/wasmJsMain/kotlin/com/cdodi/design/theme/"
        val literals = mapOf(
            "kt" to Regex("""\bColor\(\s*(?:0x|\d)"""),
            "wgsl" to Regex("""vec[34]f\(\s*\d+(?:\.\d*)?\s*,\s*\d+(?:\.\d*)?\s*,\s*\d+(?:\.\d*)?\s*(?:,[^)]*)?\)\s*/\s*255"""),
        )
        val written = roots.asSequence().flatMap { it.walk() }.filter { it.extension in literals }.flatMap { file ->
            val path = file.relativeTo(repository).invariantSeparatorsPath
            if (path.startsWith(theme)) return@flatMap emptySequence()
            val literal = literals.getValue(file.extension)
            file.readLines().asSequence().withIndex()
                .filter { (_, line) -> literal.containsMatchIn(line) && !line.trimStart().startsWith("*") && !line.trimStart().startsWith("//") }
                .map { (index, line) -> "$path:${index + 1}: ${line.trim()}" }
        }.toList()
        if (written.isNotEmpty()) throw GradleException("Colours written outside the theme:\n" + written.joinToString("\n"))
    }
}
