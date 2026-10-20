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
        jvmTest {
            // The models the site ships, so the fixture tests read exactly what's served.
            resources.srcDir(rootProject.layout.projectDirectory.dir("composeApp/src/commonMain/composeResources/files/models"))
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
