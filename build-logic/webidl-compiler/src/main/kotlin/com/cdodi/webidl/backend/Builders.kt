package com.cdodi.webidl.backend

import com.cdodi.webidl.backend.TypeMapping.Position
import com.cdodi.webidl.backend.TypeMapping.isUndefined
import com.cdodi.webidl.backend.TypeMapping.kotlinToJs
import com.cdodi.webidl.backend.TypeMapping.toFactoryParameter
import com.cdodi.webidl.backend.TypeMapping.toKotlin
import com.cdodi.webidl.model.BindingContext
import com.cdodi.webidl.model.BindingSlices
import com.cdodi.webidl.model.Descriptor
import com.cdodi.webidl.model.ExternalType
import com.cdodi.webidl.model.InterfaceMember
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT

/**
 * An IDL interface becomes an `external abstract class` when it has a constructor or extends a class (an external one
 * such as EventTarget, or a generated one): a Kotlin interface cannot extend a class. Otherwise it is an external interface.
 */
fun Descriptor.InterfaceDescriptor.isGeneratedAsClass(context: BindingContext): Boolean =
    hasConstructor || superTypes.any { superName ->
        context[BindingSlices.EXTERNAL_TYPE, superName]?.kind == ExternalType.Kind.Class ||
            context[BindingSlices.INTERFACE, superName]?.isGeneratedAsClass(context) == true
    }

fun Descriptor.InterfaceDescriptor.asInterfacePoet(context: BindingContext, generatedPackageName: String): TypeSpec {
    val interfaceBuilder = if (isGeneratedAsClass(context)) {
        TypeSpec.classBuilder(name).addModifiers(KModifier.ABSTRACT)
    } else {
        TypeSpec.interfaceBuilder(name)
    }
    interfaceBuilder.addModifiers(KModifier.EXTERNAL)

    superTypes.forEach { superName ->
        val external = context[BindingSlices.EXTERNAL_TYPE, superName]
        val generated = context[BindingSlices.INTERFACE, superName]
        when {
            superName == "JsAny" -> interfaceBuilder.addSuperinterface(ClassName("kotlin.js", "JsAny"))
            external?.kind == ExternalType.Kind.Class -> interfaceBuilder.superclass(ClassName.bestGuess(external.kotlinName))
            external != null -> interfaceBuilder.addSuperinterface(ClassName.bestGuess(external.kotlinName))
            generated?.isGeneratedAsClass(context) == true -> interfaceBuilder.superclass(ClassName(generatedPackageName, superName))
            else -> interfaceBuilder.addSuperinterface(ClassName(generatedPackageName, superName))
        }
    }

    members.filterIsInstance<InterfaceMember.VariableDescriptor>().forEach { variable ->
        val typeName = variable.type.toKotlin(context, generatedPackageName)
        interfaceBuilder.addProperty(
            PropertySpec.builder(variable.name, typeName).mutable(!variable.isReadonly).build()
        )
    }

    members.filterIsInstance<InterfaceMember.FunctionDescriptor>().forEach { function ->
        function.overloads(context, generatedPackageName).forEach { parameterTypes ->
            val funBuilder = if (function.name == "constructor") {
                FunSpec.constructorBuilder()
            } else {
                FunSpec.builder(function.name).returns(function.returnType.toKotlin(context, generatedPackageName))
            }

            function.parameters.zip(parameterTypes).forEach { (param, type) ->
                val paramSpec = ParameterSpec.builder(param.name, type)
                    .also { if (param.isOptional) it.defaultValue("definedExternally") }
                    .also { if (param.isVariadic) it.addModifiers(KModifier.VARARG) }
                    .build()
                funBuilder.addParameter(paramSpec)
            }
            interfaceBuilder.addFunction(funBuilder.build())
        }
    }

    return interfaceBuilder.build()
}


fun Descriptor.InterfaceDescriptor.asDictionaryPoet(context: BindingContext, generatedPackageName: String): TypeSpec {
    val interfaceBuilder = TypeSpec.interfaceBuilder(name)
        .addModifiers(KModifier.EXTERNAL)
        .addSuperinterface(ClassName("kotlin.js", "JsAny"))

    members.filterIsInstance<InterfaceMember.VariableDescriptor>().forEach { variable ->
        val type = variable.type.toKotlin(context, generatedPackageName)
        val typeName = type.copy(nullable = type.isNullable || !variable.isRequired)
        interfaceBuilder.addProperty(
            PropertySpec.builder(variable.name, typeName)
                .mutable(true)
                .build()
        )
    }

    return interfaceBuilder.build()
}

fun Descriptor.InterfaceDescriptor.dictFactory(
    context: BindingContext,
    generatedPackageName: String,
    runtimePackage: String,
): FunSpec {
    val createJsObjectMember = MemberName(runtimePackage, "createJsObject")
    val className = ClassName(generatedPackageName, name)
    val factoryBuilder = FunSpec.builder(name).returns(className)

    members.filterIsInstance<InterfaceMember.VariableDescriptor>().forEach { variable ->
        val typeName = variable.type.toFactoryParameter(context, generatedPackageName)
        val isOptional = !variable.isRequired
        val paramType = if (isOptional) typeName.copy(nullable = true) else typeName
        val paramBuilder = ParameterSpec.builder(variable.name, paramType)

        if (isOptional) {
            paramBuilder.defaultValue("null")
        }

        factoryBuilder.addParameter(paramBuilder.build())
    }

    factoryBuilder.beginControlFlow("return %M", createJsObjectMember)

    // Absent optional members are left out entirely: WebIDL only skips `undefined`, and null is not undefined.
    members.filterIsInstance<InterfaceMember.VariableDescriptor>().forEach { variable ->
        if (variable.isRequired) {
            val value = variable.type.kotlinToJs(context, CodeBlock.of("%N", variable.name), runtimePackage)
                ?: CodeBlock.of("%N", variable.name)
            factoryBuilder.addStatement("this.%N = %L", variable.name, value)
        } else {
            val value = variable.type.copy(isNullable = false).kotlinToJs(context, CodeBlock.of("it"), runtimePackage)
                ?: CodeBlock.of("it")
            factoryBuilder.addStatement("%N?.let { this.%N = %L }", variable.name, variable.name, value)
        }
    }

    factoryBuilder.endControlFlow()

    return factoryBuilder.build()
}


fun Descriptor.InterfaceDescriptor.asNamespacePoet(context: BindingContext, generatedPackageName: String): TypeSpec {
    val objectBuilder = TypeSpec.objectBuilder(name)

    members.filterIsInstance<InterfaceMember.ConstantDescriptor>().forEach { constant ->
        val ktType = constant.type.toKotlin(context, generatedPackageName)
        objectBuilder.addProperty(
            PropertySpec.builder(constant.name, ktType)
                .addModifiers(KModifier.CONST)
                .initializer(constant.value)
                .build()
        )
    }

    return objectBuilder.build()
}

fun Descriptor.InterfaceDescriptor.suspendWrappers(
    context: BindingContext,
    generatedPackageName: String,
    runtimePackage: String,
): List<FunSpec> {
    val awaitMember = MemberName(runtimePackage, "await")
    val className = ClassName(generatedPackageName, name)
    val wrappers = mutableListOf<FunSpec>()

    members.filterIsInstance<InterfaceMember.FunctionDescriptor>()
        .filter { it.returnType.promiseOf != null }
        .forEach { function ->
            val promiseInner = function.returnType.promiseOf!!
            val isVoid = promiseInner.isUndefined
            val returnType = if (isVoid) UNIT else promiseInner.toKotlin(context, generatedPackageName, Position.TypeArgument)
            val hasOptionalParams = function.parameters.any { it.isOptional }

            fun buildWrapper(params: List<InterfaceMember.VariableDescriptor>): FunSpec {
                val builder = FunSpec.builder("${function.name}Suspend")
                    .receiver(className)
                    .addModifiers(KModifier.SUSPEND)
                if (!isVoid) builder.returns(returnType)

                val paramNames = mutableListOf<String>()
                params.forEach { param ->
                    builder.addParameter(param.name, param.type.toKotlin(context, generatedPackageName))
                    paramNames.add(param.name)
                }

                val args = paramNames.joinToString(", ")
                val call = if (args.isEmpty()) "${function.name}()" else "${function.name}($args)"
                if (isVoid) {
                    builder.addStatement("%L.%M()", call, awaitMember)
                } else {
                    builder.addStatement("return %L.%M()", call, awaitMember)
                }
                return builder.build()
            }

            wrappers.add(buildWrapper(function.parameters))

            if (hasOptionalParams) {
                val lastRequiredIdx = function.parameters.indexOfLast { !it.isOptional }
                val firstOptionalIdx = function.parameters.indexOfFirst { it.isOptional }
                val optionalsAreTrailing = firstOptionalIdx > lastRequiredIdx
                if (optionalsAreTrailing) {
                    wrappers.add(buildWrapper(function.parameters.filter { !it.isOptional }))
                }
            }
        }

    return wrappers
}

/**
 * A WebIDL enum is a set of strings. It becomes an external interface with an empty companion object that the values hang off as extensions: `GPUPrimitiveTopology.triangleList`.
 * This is exactly how kotlinx-browser declares the DOM's enums: a companion object in an external interface is only
 * allowed with that diagnostic suppressed, and `@JsName("null")` stops Kotlin from looking up a JS global for the type.
 */
fun Descriptor.EnumDescriptor.asEnumPoet(generatedPackageName: String): TypeSpec = TypeSpec.interfaceBuilder(name)
    .addAnnotation(AnnotationSpec.builder(ClassName("kotlin.js", "JsName")).addMember("%S", "null").build())
    .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "NESTED_CLASS_IN_EXTERNAL_INTERFACE").build())
    .addModifiers(KModifier.EXTERNAL)
    .addSuperinterface(ClassName("kotlin.js", "JsAny"))
    .apply { superTypes.forEach { marker -> addSuperinterface(ClassName(generatedPackageName, marker)) } }
    .addType(TypeSpec.companionObjectBuilder().build())
    .build()

fun Descriptor.EnumDescriptor.enumValues(generatedPackageName: String): List<PropertySpec> {
    val enumType = ClassName(generatedPackageName, name)
    val toJsString = MemberName("kotlin.js", "toJsString")
    values.groupBy(::enumValueName).filterValues { it.size > 1 }.forEach { (kotlinName, clashing) ->
        error("Enum $name: values ${clashing.joinToString { "\"$it\"" }} would all be named $kotlinName")
    }

    return values.map { rawValue ->
        PropertySpec.builder(enumValueName(rawValue), enumType)
            .receiver(enumType.nestedClass("Companion"))
            .getter(
                FunSpec.getterBuilder()
                    .addModifiers(KModifier.INLINE)
                    .addStatement("return %S.%M().unsafeCast()", rawValue, toJsString)
                    .build()
            )
            .build()
    }
}

/** `"triangle-list"` -> `triangleList`; `"2d-array"` -> `2dArray` (KotlinPoet backticks names starting with a digit); `""` -> `empty`. */
fun enumValueName(rawValue: String): String {
    val words = rawValue.split('-', '_', ' ').filter(String::isNotEmpty)
    if (words.isEmpty()) return "empty"

    return words.first().replaceFirstChar(Char::lowercaseChar) +
        words.drop(1).joinToString("") { word -> word.replaceFirstChar(Char::uppercaseChar) }
}

/**
 * Members of a mixin included by an external platform type, as extension members on that type:
 * `val Navigator.gpu: GPU? get() = unsafeCast<NavigatorGPU>().gpu`.
 */
fun Descriptor.InterfaceDescriptor.extensionMembersOn(
    external: ExternalType,
    context: BindingContext,
    generatedPackageName: String,
): List<Any> {
    val receiver = ClassName.bestGuess(external.kotlinName)
    val mixinType = ClassName(generatedPackageName, name)
    val unsafeCast = MemberName("kotlin.js", "unsafeCast")

    return members.mapNotNull { member ->
        when (member) {
            is InterfaceMember.VariableDescriptor -> PropertySpec.builder(member.name, member.type.toKotlin(context, generatedPackageName))
                .receiver(receiver)
                .getter(FunSpec.getterBuilder().addStatement("return %M<%T>().%N", unsafeCast, mixinType, member.name).build())
                .build()
            is InterfaceMember.FunctionDescriptor -> FunSpec.builder(member.name)
                .receiver(receiver)
                .returns(member.returnType.toKotlin(context, generatedPackageName))
                .apply { member.parameters.forEach { addParameter(it.name, it.type.toKotlin(context, generatedPackageName)) } }
                .addStatement(
                    "return %M<%T>().%N(%L)", unsafeCast, mixinType, member.name,
                    member.parameters.joinToString { it.name },
                )
                .build()
            is InterfaceMember.ConstantDescriptor -> null
        }
    }
}

private const val MAX_OVERLOADS = 8

/**
 * Parameter types for each overload of an operation. A parameter whose type is a union without a marker interface
 * (it has a sequence, primitive or external member) would otherwise be a bare JsAny; instead it gets one overload per
 * union member, like web-sys and kotlinx-browser do. Past [MAX_OVERLOADS] combinations it stays JsAny.
 */
private fun InterfaceMember.FunctionDescriptor.overloads(context: BindingContext, pkg: String): List<List<TypeName>> {
    val alternatives = parameters.map { param ->
        val type = param.type
        val isMarker = context[BindingSlices.INTERFACE, type.name] != null
        if (type.unionMembers.isEmpty() || isMarker) {
            listOf(type.toKotlin(context, pkg))
        } else {
            type.unionMembers.map { member -> member.toKotlin(context, pkg).let { it.copy(nullable = it.isNullable || type.isNullable) } }
        }
    }
    val combinations = alternatives.fold(listOf(emptyList<TypeName>())) { acc, options -> acc.flatMap { prefix -> options.map { prefix + it } } }

    return if (combinations.size <= MAX_OVERLOADS) combinations else listOf(parameters.map { it.type.toKotlin(context, pkg) })
}
