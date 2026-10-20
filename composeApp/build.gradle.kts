import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
            // Chrome flags and the test timeout live in karma.config.d/webgpu.js, so GPU tests get an adapter.
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
    }

    compilerOptions {
        optIn = listOf(
            "androidx.compose.ui.ExperimentalComposeUiApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "org.jetbrains.compose.resources.ExperimentalResourceApi",
        )
    }

    sourceSets {

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(projects.core)
            implementation(projects.gltf)
            implementation(projects.notes)
            implementation(projects.designSystem)
        }

        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
            implementation(projects.webgpu)
            implementation(projects.webaudio)
        }

        wasmJsTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.withType<KotlinJsTest>().configureEach {
    // Chrome flags live there; editing them must rerun the tests.
    inputs.dir("karma.config.d").withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("karmaConfig")
}

/**
 * The notes as plain HTML pages, beside the app: `/notes/<slug>.html`, `/notes/`, `sitemap.xml` and `robots.txt`.
 * The app draws everything on a canvas; these are what search engines, link previews and browsers without WasmGC read.
 * They come from the same Markdown the app renders (`composeResources/files/notes`), by the notes module's JVM build.
 */
val notesSiteClasspath = configurations.create("notesSiteClasspath") {
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
    }
}

dependencies {
    notesSiteClasspath(projects.notes)
}

val notesSource = layout.projectDirectory.dir("src/commonMain/composeResources/files/notes")
val notesSite = layout.buildDirectory.dir("generated/notesSite")

val generateNotesSite = tasks.register<JavaExec>("generateNotesSite") {
    group = "build"
    description = "Writes the notes as plain HTML pages, with a sitemap and robots.txt."
    classpath = notesSiteClasspath
    mainClass = "com.cdodi.notes.site.GenerateNotesSiteKt"
    inputs.dir(notesSource).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("notes")
    outputs.dir(notesSite).withPropertyName("site")
    val source = notesSource.asFile
    val output = notesSite.map { it.asFile }
    argumentProviders += CommandLineArgumentProvider {
        listOf(source.path, output.get().path, "https://maximums.github.io", "Dodi Cristian-Dumitru")
    }
}

kotlin.sourceSets.named("wasmJsMain") {
    resources.srcDir(files(notesSite).builtBy(generateNotesSite))
}

/**
 * ARCHITECTURE.md §4.9 as a check, since inside one module the compiler can't enforce it: the app's code lives in
 * `shell`, `features` and `adapters` only, and each imports only what its rule allows. The design system is a module
 * of its own (`:design-system`), so the build enforces its rule: it depends on nothing of the app's.
 */
val checkPackageRules = tasks.register("checkPackageRules") {
    group = "verification"
    description = "Fails when the app's packages import across the rules in ARCHITECTURE.md §4.9."
    val sources = layout.projectDirectory.dir("src/wasmJsMain/kotlin/com/cdodi")
    inputs.dir(sources).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("sources")
    val root = sources.asFile
    doLast {
        val layers = setOf("shell", "features", "adapters")
        val strays = root.listFiles().orEmpty().filter { it.isDirectory && it.name !in layers }.map { "${it.name}/ isn't one of $layers" }
        val crossings = root.walk().filter { it.extension == "kt" }.flatMap { file ->
            val path = file.relativeTo(root).invariantSeparatorsPath
            val layer = path.substringBefore('/')
            val feature = path.split('/').getOrNull(1)
            Regex("""^import com\.cdodi\.(\w+)\.(\w+)""", RegexOption.MULTILINE).findAll(file.readText()).mapNotNull { match ->
                val (target, sub) = match.destructured
                val allowed = when (layer) {
                    "features" -> target != "shell" && (target != "features" || sub == feature)
                    "adapters" -> target != "shell" && target != "features"
                    else -> true
                }
                if (allowed) null else "$path imports com.cdodi.$target.$sub"
            }
        }.toList()
        val broken = strays + crossings
        if (broken.isNotEmpty()) throw GradleException("The package rules (ARCHITECTURE.md §4.9) are broken:\n" + broken.joinToString("\n"))
    }
}

tasks.named("check") { dependsOn(checkPackageRules, ":checkThemeTokens") }
