import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("com.cdodi.webidl")
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    compilerOptions {
        optIn.add("kotlin.js.ExperimentalWasmJsInterop")
    }
}

webIdl {
    idlFiles.from("idl/webgpu.idl")
    packageName = "com.cdodi.webgpu.bindings"
    runtimePackage = "com.cdodi.webgpu.runtime"
    apiFileName = "WebGpuBindings"
    factoriesFileName = "WebGpuFactories"
}
