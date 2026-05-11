import org.gradle.kotlin.dsl.`kotlin-dsl`

plugins {
    `kotlin-dsl`
    id("cdodi.antrl-setup")
}

group = "com.cdodi.plugins.webIdlBinding"
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
        register("webIdlBindings") {
            id = "com.cdodi.webidl.bindings"
            displayName = "WebIDL Bindings"
            description = "Generates Kotlin/Wasm bindings from WebIDL specifications"
            implementationClass = "com.cdodi.WebIdlBindingsPlugin"
        }
    }
}
