import org.gradle.kotlin.dsl.`kotlin-dsl`

plugins {
    `kotlin-dsl`
    id("cdodi.antrl-setup")
}

group = "com.cdodi.plugins.webGpuBinding"
version = "0.1.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    compileOnly(kotlin("gradle-plugin"))
    implementation(libs.kotlin.poet)
}

gradlePlugin {
    plugins {
        register("webGpuBindings") {
            id = "com.cdodi.webgpu.bindings"
            displayName = "WebGPU Bindings"
            description = "Generates Kotlin bindings for WebGPU"
            implementationClass = "com.cdodi.WebGpuBindingsPlugin"
        }
    }
}
