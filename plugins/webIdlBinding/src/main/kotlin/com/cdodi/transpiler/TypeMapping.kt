package com.cdodi.transpiler

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT

/**
 * Centralizes all WebIDL → Kotlin type mapping decisions.
 *
 * Two flavors:
 * - JS-facing (`asPoetJs`): types used in `external` declarations — maps to JsNumber, JsString, etc.
 * - Kt-facing (`asPoetKt`): types used in factory functions — maps to Kotlin primitives.
 */
object TypeMapping {

    private val jsAny = ClassName("kotlin.js", "JsAny")
    private val jsNumber = ClassName("kotlin.js", "JsNumber")
    private val jsBoolean = ClassName("kotlin.js", "JsBoolean")
    private val jsString = ClassName("kotlin.js", "JsString")
    private val jsArray = ClassName("kotlin.js", "JsArray")
    private val jsPromise = ClassName("kotlin.js", "Promise")

    // --- JS-facing type mapping (for external declarations) ---

    fun Descriptor.TypeDescriptor.asPoetJs(context: BindingContext, pkg: String): TypeName = when {
        isKnownDescriptor(context) -> ClassName(pkg, name)
        promiseOf != null -> promiseOf.asPoetJs(context, pkg).wrapWith(jsPromise)
        unionMembers.isNotEmpty() -> resolveUnionJs(context, pkg)
        sequenceOf != null -> sequenceOf.asPoetJs(context, pkg).wrapWith(jsArray)
        record != null -> resolveRecordJs(context, pkg)
        else -> mapPrimitiveJs()
    }

    // --- Kt-facing type mapping (for factory/bridge functions) ---

    fun Descriptor.TypeDescriptor.asPoetKt(context: BindingContext, pkg: String): TypeName = when {
        isKnownDescriptor(context) -> ClassName(pkg, name)
        promiseOf != null -> {
            val inner = promiseOf.asPoetKt(context, pkg)
            ClassName("kotlinx.coroutines", "Deferred").parameterizedBy(inner)
        }
        unionMembers.isNotEmpty() -> resolveUnionKt(context, pkg)
        sequenceOf != null -> {
            val inner = sequenceOf.asPoetKt(context, pkg)
            LIST.parameterizedBy(inner)
        }
        record != null -> asPoetJs(context, pkg) // records remain JsAny (no Kotlin-native mapping yet)
        else -> mapPrimitiveKt()
    }

    // --- Kt→Js conversion bridge ---

    val TypeName.conversionBridge: MemberName?
        get() = when (this) {
            BOOLEAN -> MemberName("kotlin.js", "toJsBoolean")
            STRING -> MemberName("kotlin.js", "toJsString")
            INT, DOUBLE -> MemberName("kotlin.js", "toJsNumber")
            else -> null
        }

    // --- Private helpers ---

    private fun Descriptor.TypeDescriptor.isKnownDescriptor(context: BindingContext): Boolean =
        context[BindingSlices.INTERFACE, name] != null
                || context[BindingSlices.DICTIONARY, name] != null
                || context[BindingSlices.ENUM, name] != null

    private fun Descriptor.TypeDescriptor.resolveUnionJs(context: BindingContext, pkg: String): TypeName {
        val markerName = unionMembers.joinToString(separator = "Or") { it.name }
        val resolved = context[BindingSlices.INTERFACE, markerName] ?: context[BindingSlices.DICTIONARY, markerName]
        return resolved?.let { ClassName(pkg, it.name) } ?: jsAny
    }

    private fun Descriptor.TypeDescriptor.resolveUnionKt(context: BindingContext, pkg: String): TypeName {
        val markerName = unionMembers.joinToString(separator = "Or") { it.name }
        val resolved = context[BindingSlices.INTERFACE, markerName] ?: context[BindingSlices.DICTIONARY, markerName]
        return resolved?.let { ClassName(pkg, it.name) } ?: jsAny
    }

    private fun Descriptor.TypeDescriptor.resolveRecordJs(context: BindingContext, pkg: String): TypeName {
        val keyType = record!!.keys.first().asPoetJs(context, pkg)
        val valueType = record.values.first().asPoetJs(context, pkg)
        return jsAny // JS records don't have a direct JsArray/JsMap equivalent; fallback to JsAny
    }

    private fun Descriptor.TypeDescriptor.mapPrimitiveJs(): TypeName =
        when (name) {
            "byte", "octet",
            "short", "unsignedshort",
            "long", "unsignedlong", "longlong", "unsignedlonglong",
            "float", "unrestrictedfloat",
            "double", "unrestricteddouble" -> jsNumber
            "boolean" -> jsBoolean
            "DOMString", "USVString", "ByteString" -> jsString
            else -> jsAny
        }.copy(nullable = true)

    private fun Descriptor.TypeDescriptor.mapPrimitiveKt(): TypeName =
        when (name) {
            "byte", "octet", "short", "unsignedshort",
            "long", "unsignedlong",
            "longlong", "unsignedlonglong" -> INT
            "float", "unrestrictedfloat",
            "double", "unrestricteddouble" -> DOUBLE
            "boolean" -> BOOLEAN
            "DOMString", "USVString", "ByteString" -> STRING
            "void", "undefined" -> UNIT
            else -> jsAny
        }.copy(nullable = isNullable)

    private fun TypeName.wrapWith(wrapper: ClassName): TypeName =
        wrapper.parameterizedBy(this)
}
