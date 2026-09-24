package com.cdodi.webidl.backend

import com.cdodi.webidl.model.BindingContext
import com.cdodi.webidl.model.BindingSlices
import com.cdodi.webidl.model.Descriptor
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT

/**
 * Every WebIDL -> Kotlin/Wasm type decision.
 *
 * Kotlin/Wasm externals take Kotlin primitives directly (`Int`, `Double`, `Boolean`, `String`), so those are used
 * wherever the language allows. Only type arguments of `Promise<T>` and `JsArray<T>` must be `JsAny?` subtypes,
 * which is what [Position.TypeArgument] is for. Nullability always comes from the IDL's `?`.
 */
object TypeMapping {

    enum class Position {
        /** Parameter, property or return type. */
        Value,

        /** Type argument of `Promise` or `JsArray`: must be a `JsAny?` subtype. */
        TypeArgument,
    }

    private val jsAny = ClassName("kotlin.js", "JsAny")
    private val jsNumber = ClassName("kotlin.js", "JsNumber")
    private val jsBoolean = ClassName("kotlin.js", "JsBoolean")
    private val jsString = ClassName("kotlin.js", "JsString")
    private val jsArray = ClassName("kotlin.js", "JsArray")
    private val jsPromise = ClassName("kotlin.js", "Promise")

    /** WebIDL primitive names as the parser spells them (tokens without spaces). */
    private enum class Primitive(val value: TypeName, val typeArgument: TypeName, vararg val idlNames: String) {
        // 32-bit and smaller integers fit a Kotlin Int. `unsigned long` flags and limits stay below 2^31 in practice.
        Integer(INT, ClassName("kotlin.js", "JsNumber"), "byte", "octet", "short", "unsignedshort", "long", "unsignedlong"),

        // `long long` is a JS Number (at most 2^53 - 1), never a BigInt, so it must not become a Kotlin Long.
        WideInteger(DOUBLE, ClassName("kotlin.js", "JsNumber"), "longlong", "unsignedlonglong"),
        Decimal(DOUBLE, ClassName("kotlin.js", "JsNumber"), "double", "unrestricteddouble"),
        Single(FLOAT, ClassName("kotlin.js", "JsNumber"), "float", "unrestrictedfloat"),
        Bool(BOOLEAN, ClassName("kotlin.js", "JsBoolean"), "boolean"),
        Text(STRING, ClassName("kotlin.js", "JsString"), "DOMString", "USVString", "ByteString");

        companion object {
            private val byName = entries.flatMap { primitive -> primitive.idlNames.map { it to primitive } }.toMap()
            fun of(idlName: String): Primitive? = byName[idlName]
        }
    }

    fun Descriptor.TypeDescriptor.toKotlin(context: BindingContext, pkg: String, position: Position = Position.Value): TypeName {
        val type: TypeName = when {
            isKnownDescriptor(context) -> ClassName(pkg, name)
            promiseOf != null -> jsPromise.parameterizedBy(promiseOf.toKotlin(context, pkg, Position.TypeArgument))
            unionMembers.isNotEmpty() -> resolveUnion(context, pkg)
            sequenceOf != null -> jsArray.parameterizedBy(sequenceOf.toKotlin(context, pkg, Position.TypeArgument))
            record != null -> jsAny
            isUndefined -> if (position == Position.Value) UNIT else jsAny.copy(nullable = true)
            name == "any" -> jsAny.copy(nullable = true)
            else -> Primitive.of(name)?.let { if (position == Position.Value) it.value else it.typeArgument } ?: jsAny
        }
        return type.copy(nullable = type.isNullable || isNullable)
    }

    /**
     * The type a dictionary factory takes for a member: like [toKotlin], except that sequences are Kotlin `List`s
     * of Kotlin types, converted by [kotlinToJs].
     */
    fun Descriptor.TypeDescriptor.toFactoryParameter(context: BindingContext, pkg: String): TypeName =
        if (sequenceOf != null) {
            LIST.parameterizedBy(sequenceOf.toFactoryParameter(context, pkg)).copy(nullable = isNullable)
        } else {
            toKotlin(context, pkg)
        }

    /**
     * Converts a factory parameter ([toFactoryParameter]) into the dictionary property's type, or returns null when
     * the value can be assigned as it is.
     */
    fun Descriptor.TypeDescriptor.kotlinToJs(context: BindingContext, value: CodeBlock, runtimePackage: String): CodeBlock? {
        val safeCall = if (isNullable) "?." else "."

        if (sequenceOf != null) {
            val toJsArray = MemberName(runtimePackage, "toJsArray")
            val element = sequenceOf.elementToJs(context, runtimePackage)
            return if (element == null) {
                CodeBlock.of("%L%L%M()", value, safeCall, toJsArray)
            } else {
                CodeBlock.of("%L%Lmap { %L }%L%M()", value, safeCall, element, if (isNullable) "?." else ".", toJsArray)
            }
        }
        return null
    }

    /** Conversion of one sequence element `it` from its Kotlin type to its `JsAny?` type, or null if none is needed. */
    private fun Descriptor.TypeDescriptor.elementToJs(context: BindingContext, runtimePackage: String): CodeBlock? {
        val safeCall = if (isNullable) "?." else "."
        val bridge = when (Primitive.of(name)?.takeUnless { isKnownDescriptor(context) }) {
            Primitive.Integer, Primitive.WideInteger, Primitive.Decimal, Primitive.Single -> "toJsNumber"
            Primitive.Bool -> "toJsBoolean"
            Primitive.Text -> "toJsString"
            null -> null
        }
        if (bridge != null) return CodeBlock.of("it%L%M()", safeCall, MemberName("kotlin.js", bridge))
        if (sequenceOf != null) return kotlinToJs(context, CodeBlock.of("it"), runtimePackage)
        return null
    }

    val Descriptor.TypeDescriptor.isUndefined: Boolean
        get() = name == "undefined" || name == "void"

    private fun Descriptor.TypeDescriptor.isKnownDescriptor(context: BindingContext): Boolean =
        context[BindingSlices.INTERFACE, name] != null
                || context[BindingSlices.DICTIONARY, name] != null
                || context[BindingSlices.ENUM, name] != null

    private fun Descriptor.TypeDescriptor.resolveUnion(context: BindingContext, pkg: String): TypeName {
        val markerName = unionMembers.joinToString(separator = "Or") { it.name }
        val resolved = context[BindingSlices.INTERFACE, markerName] ?: context[BindingSlices.DICTIONARY, markerName]
        return resolved?.let { ClassName(pkg, it.name) } ?: jsAny
    }
}
