import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

/**
 * The write-ups: a Markdown parser for the subset they use, and an HTML renderer. The site renders the parsed notes
 * with Compose (wasmJs); the JVM target writes them as plain HTML pages at build time, for search engines, link
 * previews and browsers that can't run the site.
 */
kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
