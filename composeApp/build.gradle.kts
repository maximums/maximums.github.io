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
            implementation(projects.notes)
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
