package com.cdodi.webidl.model

/**
 * A type the IDL uses but does not define (EventTarget, ArrayBuffer, ...), mapped to an existing Kotlin/Wasm type.
 *
 * [kind] matters for inheritance: an IDL interface extending an external [Kind.Class] must itself become an
 * `external abstract class`, because a Kotlin interface cannot extend a class. A [Kind.Value] type is only ever used
 * as a value; as a supertype it is dropped.
 */
data class ExternalType(val idlName: String, val kotlinName: String, val kind: Kind, val isNullable: Boolean = false) {

    enum class Kind { Class, Interface, Value }

    companion object {
        /**
         * Parses the `webIdl { externalTypes }` notation: `"class org.w3c.dom.events.EventTarget"`,
         * `"interface org.khronos.webgl.BufferDataSource"` or `"value kotlin.js.JsAny?"` (a trailing `?` = nullable).
         */
        fun parse(idlName: String, notation: String): ExternalType {
            val parts = notation.trim().split(Regex("\\s+"))
            require(parts.size == 2) { "External type '$idlName': expected '<class|interface|value> <Kotlin type>', got '$notation'" }

            val kind = Kind.entries.firstOrNull { it.name.equals(parts[0], ignoreCase = true) }
                ?: throw IllegalArgumentException("External type '$idlName': unknown kind '${parts[0]}' in '$notation'")
            val kotlinName = parts[1].removeSuffix("?")

            return ExternalType(idlName, kotlinName, kind, isNullable = parts[1].endsWith("?"))
        }

        fun parseAll(notations: Map<String, String>): Map<String, ExternalType> =
            notations.mapValues { (idlName, notation) -> parse(idlName, notation) }

        /** Common Web platform types, mapped to kotlinx-browser. Modules add to or override these. */
        val DEFAULTS: Map<String, String> = mapOf(
            "EventTarget" to "class org.w3c.dom.events.EventTarget",
            "Event" to "class org.w3c.dom.events.Event",
            "EventInit" to "interface org.w3c.dom.EventInit",
            "ArrayBuffer" to "class org.khronos.webgl.ArrayBuffer",
            "ArrayBufferView" to "interface org.khronos.webgl.ArrayBufferView",
            "BufferSource" to "interface org.khronos.webgl.BufferDataSource",
            "AllowSharedBufferSource" to "interface org.khronos.webgl.BufferDataSource",
            "Uint32Array" to "class org.khronos.webgl.Uint32Array",
            "ImageBitmap" to "class org.w3c.dom.ImageBitmap",
            "ImageData" to "class org.w3c.dom.ImageData",
            "HTMLCanvasElement" to "class org.w3c.dom.HTMLCanvasElement",
            "HTMLImageElement" to "class org.w3c.dom.HTMLImageElement",
            "HTMLVideoElement" to "class org.w3c.dom.HTMLVideoElement",
            "Navigator" to "class org.w3c.dom.Navigator",
            "WorkerNavigator" to "class org.w3c.dom.WorkerNavigator",
            // Not in kotlinx-browser (yet): passed through as plain JS values.
            "DOMException" to "value kotlin.js.JsAny",
            "OffscreenCanvas" to "value kotlin.js.JsAny",
            "VideoFrame" to "value kotlin.js.JsAny",
            "EventHandler" to "value kotlin.js.JsAny?",
            "PredefinedColorSpace" to "value kotlin.String",
        )
    }
}
