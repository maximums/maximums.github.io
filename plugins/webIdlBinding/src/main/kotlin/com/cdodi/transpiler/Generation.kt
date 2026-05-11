package com.cdodi.transpiler

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec

fun generateKotlin(
    context: BindingContext,
    generatedPackageName: String,
    apiFileName: String,
    factoriesFileName: String,
): List<FileSpec> {
    val fileComment = "Current file is generated, please don't modify it manually because your changes will be lost."
    val fileAnnotation = AnnotationSpec.builder(Suppress::class).addMember(
        "%S, %S, %S, %S, %S",
        "Unused",
        "RedundantVisibilityModifier",
        "RemoveRedundantBackticks",
        "ObjectPropertyName",
        "RemoveRedundantQualifierName"
    ).build()
    val apiFileBuilder = FileSpec.builder(generatedPackageName, apiFileName)
        .addFileComment(fileComment)
        .addAnnotation(fileAnnotation)
    val factoriesFileBuilder = FileSpec.builder(generatedPackageName, factoriesFileName)
        .addFileComment(fileComment)
        .addAnnotation(fileAnnotation)

    context[BindingSlices.ENUM]?.values?.forEach { enumDesc ->
        val enumInterface = enumDesc.asEnumPoet()
        val enumEntries = enumDesc.enumFactory(generatedPackageName)
        apiFileBuilder.addType(enumInterface)
        factoriesFileBuilder.addType(enumEntries)
    }

    context[BindingSlices.INTERFACE]?.values?.forEach { interfaceDesc ->
        val interfaceSpec = interfaceDesc.asInterfacePoet(context, generatedPackageName)
        apiFileBuilder.addType(interfaceSpec)
    }

    context[BindingSlices.DICTIONARY]?.values?.forEach { dictDesc ->
        val dictInterface = dictDesc.asDictionaryPoet(context, generatedPackageName)
        val dictFactoryFun = dictDesc.dictFactory(context, generatedPackageName)
        apiFileBuilder.addType(dictInterface)
        factoriesFileBuilder.addFunction(dictFactoryFun)
    }

    return listOf(apiFileBuilder.build(), factoriesFileBuilder.build())
}