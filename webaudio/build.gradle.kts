import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("com.cdodi.webidl")
}

/** Web Audio bindings, generated from the spec's WebIDL by the same compiler as `:webgpu`'s, on the same runtime. */
kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    compilerOptions {
        optIn.add("kotlin.js.ExperimentalWasmJsInterop")
    }

    sourceSets {
        wasmJsMain.dependencies {
            // Generated declarations use kotlinx-browser's types (EventTarget, MediaStream, typed arrays, ...).
            api(libs.kotlinx.browser)
            api(projects.webidlRuntime)
        }
    }
}

webIdl {
    idlFiles.from("idl/webaudio.idl")
    packageName = "com.cdodi.webaudio.bindings"
    runtimePackage = "com.cdodi.webidl.runtime"
    fileNamePrefix = "WebAudio"
    specUrl = "https://webaudio.github.io/web-audio-api/"

    // Types from other specs: kotlinx-browser's where it has them, plain JS values for the rest.
    externalClass("Float32Array", "org.khronos.webgl.Float32Array")
    externalClass("Uint8Array", "org.khronos.webgl.Uint8Array")
    externalClass("HTMLMediaElement", "org.w3c.dom.HTMLMediaElement")
    externalClass("MediaStream", "org.w3c.dom.mediacapture.MediaStream")
    externalClass("MediaStreamTrack", "org.w3c.dom.mediacapture.MediaStreamTrack")
    externalClass("MessagePort", "org.w3c.dom.MessagePort")
    externalTypes.put("DOMHighResTimeStamp", "value kotlin.Double")
    externalTypes.put("Worklet", "value kotlin.js.JsAny")
    externalTypes.put("WorkletGlobalScope", "value kotlin.js.JsAny")
    // Callbacks aren't modelled by the compiler yet. decodeAudioData's have a promise form instead, and worklet
    // processors are classes registered from a worklet's own script, which this site doesn't write.
    externalTypes.put("DecodeSuccessCallback", "value kotlin.js.JsAny")
    externalTypes.put("DecodeErrorCallback", "value kotlin.js.JsAny")
    externalTypes.put("AudioWorkletProcessorConstructor", "value kotlin.js.JsAny")
}
