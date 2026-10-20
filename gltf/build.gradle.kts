import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * Reads glTF 2.0 models from `.glb` files into what a renderer needs: vertex arrays, instances, materials and the
 * images' bytes. It knows no GPU and no browser (the app uploads the arrays and the browser decodes the images). Pure
 * Kotlin; the JVM target exists so the tests run in milliseconds, and reads the committed fixtures.
 */
kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.math)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
