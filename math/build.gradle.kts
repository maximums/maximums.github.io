import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

/**
 * The geometry the 3D pieces share: vectors, rotations, 4×4 matrices and boxes, in single precision and laid out as
 * WGSL expects. Pure Kotlin; the JVM target exists only so the tests run in milliseconds without a browser.
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
