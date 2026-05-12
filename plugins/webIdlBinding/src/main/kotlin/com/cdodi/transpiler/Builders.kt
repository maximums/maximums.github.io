package com.cdodi.transpiler

import com.cdodi.transpiler.TypeMapping.asPoetJs
import com.cdodi.transpiler.TypeMapping.asPoetKt
import com.cdodi.transpiler.TypeMapping.conversionBridge
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT

fun Descriptor.InterfaceDescriptor.asInterfacePoet(context: BindingContext, generatedPackageName: String): TypeSpec {
    val interfaceBuilder = if (hasConstructor) {
        TypeSpec.classBuilder(name).addModifiers(KModifier.ABSTRACT)
    } else {
        TypeSpec.interfaceBuilder(name)
    }
    interfaceBuilder.addModifiers(KModifier.EXTERNAL)

    superTypes.forEach { superName ->
        interfaceBuilder.addSuperinterface(ClassName(generatedPackageName, superName))
    }

    members.filterIsInstance<InterfaceMember.VariableDescriptor>().forEach { variable ->
        val typeName = variable.type.asPoetJs(context, generatedPackageName)
        interfaceBuilder.addProperty(
            PropertySpec.builder(variable.name, typeName).mutable(!variable.isReadonly).build()
        )
    }

    members.filterIsInstance<InterfaceMember.FunctionDescriptor>().forEach { function ->
        val funBuilder = if (function.name == "constructor") {
            FunSpec.constructorBuilder()
        } else {
            FunSpec.builder(function.name).returns(function.returnType.asPoetJs(context, generatedPackageName))
        }

        function.parameters.forEach { param ->
            val paramSpec = ParameterSpec.builder(param.name, param.type.asPoetJs(context, generatedPackageName))
                .also { if (param.defaultValue != null) it.defaultValue("definedExternally") }
                .build()
            funBuilder.addParameter(paramSpec)
        }
        interfaceBuilder.addFunction(funBuilder.build())
    }

    return interfaceBuilder.build()
}


fun Descriptor.InterfaceDescriptor.asDictionaryPoet(context: BindingContext, generatedPackageName: String): TypeSpec {
    val interfaceBuilder = TypeSpec.interfaceBuilder(name)
        .addModifiers(KModifier.EXTERNAL)
        .addSuperinterface(ClassName("kotlin.js", "JsAny"))

    members.filterIsInstance<InterfaceMember.VariableDescriptor>().forEach { variable ->
        val typeName = variable.type.asPoetJs(context, generatedPackageName)
            .copy(nullable = !variable.isRequired)
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
    runtimePackage: String = "com.cdodi.webgpu",
): FunSpec {
    val createJsObjectMember = MemberName(runtimePackage, "createJsObject")
    val className = ClassName(generatedPackageName, name)
    val factoryBuilder = FunSpec.builder(name).returns(className)

    members.filterIsInstance<InterfaceMember.VariableDescriptor>().forEach { variable ->
        val typeName = variable.type.asPoetKt(context, generatedPackageName)
        val isOptional = !variable.isRequired
        val paramType = if (isOptional) typeName.copy(nullable = true) else typeName
        val paramBuilder = ParameterSpec.builder(variable.name, paramType)

        if (isOptional) {
            paramBuilder.defaultValue("null")
        }

        factoryBuilder.addParameter(paramBuilder.build())
    }

    factoryBuilder.beginControlFlow("return %M", createJsObjectMember)

    members.filterIsInstance<InterfaceMember.VariableDescriptor>().forEach { variable ->
        val typeName = variable.type.asPoetKt(context, generatedPackageName)
        val isOptional = !variable.isRequired
        val conversion = when {
            variable.type.sequenceOf != null -> MemberName(runtimePackage, "toJsArray")
            else -> typeName.copy(nullable = false).conversionBridge
        }

        if (isOptional) {
            if (conversion != null) {
                factoryBuilder.addStatement("%N?.let { this.%N = it.%M() }", variable.name, variable.name, conversion)
            } else {
                factoryBuilder.addStatement("%N?.let { this.%N = it }", variable.name, variable.name)
            }
        } else {
            if (conversion != null) {
                factoryBuilder.addStatement("this.%N = %N.%M()", variable.name, variable.name, conversion)
            } else {
                factoryBuilder.addStatement("this.%N = %N", variable.name, variable.name)
            }
        }
    }

    factoryBuilder.endControlFlow()

    return factoryBuilder.build()
}


fun Descriptor.InterfaceDescriptor.asNamespacePoet(context: BindingContext, generatedPackageName: String): TypeSpec {
    val objectBuilder = TypeSpec.objectBuilder(name)

    members.filterIsInstance<InterfaceMember.ConstantDescriptor>().forEach { constant ->
        val ktType = constant.type.asPoetKt(context, generatedPackageName)
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
    runtimePackage: String = "com.cdodi.webgpu",
): List<FunSpec> {
    val awaitMember = MemberName(runtimePackage, "await")
    val className = ClassName(generatedPackageName, name)
    val wrappers = mutableListOf<FunSpec>()

    members.filterIsInstance<InterfaceMember.FunctionDescriptor>()
        .filter { it.returnType.promiseOf != null }
        .forEach { function ->
            val promiseInner = function.returnType.promiseOf!!
            val isVoid = promiseInner.name in listOf("undefined", "void")
            val returnType = if (isVoid) UNIT else promiseInner.asPoetJs(context, generatedPackageName)
            val hasOptionalParams = function.parameters.any { it.defaultValue != null }

            fun buildWrapper(params: List<InterfaceMember.VariableDescriptor>): FunSpec {
                val builder = FunSpec.builder("${function.name}Suspend")
                    .receiver(className)
                    .addModifiers(KModifier.SUSPEND)
                if (!isVoid) builder.returns(returnType)

                val paramNames = mutableListOf<String>()
                params.forEach { param ->
                    builder.addParameter(param.name, param.type.asPoetJs(context, generatedPackageName))
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
                val lastRequiredIdx = function.parameters.indexOfLast { it.defaultValue == null }
                val firstOptionalIdx = function.parameters.indexOfFirst { it.defaultValue != null }
                val optionalsAreTrailing = firstOptionalIdx > lastRequiredIdx
                if (optionalsAreTrailing) {
                    wrappers.add(buildWrapper(function.parameters.filter { it.defaultValue == null }))
                }
            }
        }

    return wrappers
}

fun Descriptor.EnumDescriptor.asEnumPoet(): TypeSpec {
    val interfaceBuilder = TypeSpec.interfaceBuilder(name)
        .addModifiers(KModifier.SEALED, KModifier.EXTERNAL)
        .addSuperinterface(ClassName("kotlin.js", "JsAny"))

    return interfaceBuilder.build()
}

fun Descriptor.EnumDescriptor.enumFactory(generatedPackageName: String): TypeSpec {
    val className = ClassName(generatedPackageName, name)
    val objectName = "${name}Entries"
    val objectBuilder = TypeSpec.objectBuilder(objectName)
    val toJsStringMember = MemberName("kotlin.js", "toJsString")
    values.forEach { rawValue ->
        val safeName = "`$rawValue`"
        val getter = FunSpec.getterBuilder()
            .addModifiers(KModifier.INLINE)
            .addStatement("return %S.%M().unsafeCast()", rawValue, toJsStringMember)
            .build()

        val property = PropertySpec.builder(safeName, className)
            .getter(getter)
            .build()

        objectBuilder.addProperty(property)
    }
    return objectBuilder.build()
}
