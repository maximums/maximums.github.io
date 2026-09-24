import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("com.cdodi.webidl")
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // Chrome flags and the test timeout live in karma.config.d/webgpu.js.
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }

    compilerOptions {
        optIn.add("kotlin.js.ExperimentalWasmJsInterop")
    }

    sourceSets {
        wasmJsMain.dependencies {
            // Generated declarations extend and use kotlinx-browser types (EventTarget, ArrayBuffer, ...).
            api(libs.kotlinx.browser)
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

webIdl {
    idlFiles.from("idl/webgpu.idl")
    packageName = "com.cdodi.webgpu.bindings"
    runtimePackage = "com.cdodi.webgpu.runtime"
    fileNamePrefix = "WebGpu"
}
