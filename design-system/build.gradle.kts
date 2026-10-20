import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/**
 * The site's design system (DESIGN.md, "Autumn Rain"): the theme as a value (colours by role, glass, scene, type and
 * its fonts), the viewport, and the components built on them. Compose only. It knows nothing of the app, which gives it
 * what it needs through its ports: a GlassSink for the GPU's glass, a MotionClock for UI motion.
 */
kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        // Not a site of its own: Compose's tests need an executable binary, so Skiko's runtime is bundled for them.
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
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.material)
            api(libs.compose.ui)
            implementation(libs.compose.components.resources)
        }

        wasmJsTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// The fonts are the design system's resources: generated as com.cdodi.design.resources.Res, for its own code only.
compose.resources {
    packageOfResClass = "com.cdodi.design.resources"
}
