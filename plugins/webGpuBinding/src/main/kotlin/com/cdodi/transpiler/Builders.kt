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
        val typeName = variable.type.asPoetJs(context, generatedPackageName).copy(nullable = true)
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
        val paramBuilder = ParameterSpec.builder(variable.name, typeName)

        if (variable.defaultValue != null) {
            paramBuilder.defaultValue("null")
        }

        factoryBuilder.addParameter(paramBuilder.build())
    }

    factoryBuilder.beginControlFlow("return %M", createJsObjectMember)

    members.filterIsInstance<InterfaceMember.VariableDescriptor>().forEach { variable ->
        val typeName = variable.type.asPoetKt(context, generatedPackageName)
        val member = typeName.conversionBridge
        if (member != null) {
            factoryBuilder.addStatement("this.%N = %M", variable.name, member)
        } else {
            factoryBuilder.addStatement("this.%N = %N", variable.name, variable.name)
        }
    }

    factoryBuilder.endControlFlow()

    return factoryBuilder.build()
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
