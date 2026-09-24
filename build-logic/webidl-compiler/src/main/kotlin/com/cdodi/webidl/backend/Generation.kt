package com.cdodi.webidl.backend

import com.cdodi.webidl.model.BindingContext
import com.cdodi.webidl.model.BindingSlices
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec

fun generateKotlin(
    context: BindingContext,
    generatedPackageName: String,
    runtimePackage: String,
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
        apiFileBuilder.addType(enumDesc.asEnumPoet())
        enumDesc.enumValues(generatedPackageName).forEach(factoriesFileBuilder::addProperty)
    }

    context[BindingSlices.INTERFACE]?.values?.forEach { interfaceDesc ->
        val interfaceSpec = interfaceDesc.asInterfacePoet(context, generatedPackageName)
        apiFileBuilder.addType(interfaceSpec)
        interfaceDesc.suspendWrappers(context, generatedPackageName, runtimePackage).forEach { suspendFun ->
            factoriesFileBuilder.addFunction(suspendFun)
        }
    }

    context[BindingSlices.DICTIONARY]?.values?.forEach { dictDesc ->
        val dictInterface = dictDesc.asDictionaryPoet(context, generatedPackageName)
        val dictFactoryFun = dictDesc.dictFactory(context, generatedPackageName, runtimePackage)
        apiFileBuilder.addType(dictInterface)
        factoriesFileBuilder.addFunction(dictFactoryFun)
    }

    context[BindingSlices.EXTERNAL_INCLUDES]?.forEach { (externalName, mixinNames) ->
        val external = context[BindingSlices.EXTERNAL_TYPE, externalName] ?: return@forEach
        mixinNames.mapNotNull { context[BindingSlices.INTERFACE, it] }.forEach { mixin ->
            mixin.extensionMembersOn(external, context, generatedPackageName).forEach { member ->
                when (member) {
                    is PropertySpec -> factoriesFileBuilder.addProperty(member)
                    is FunSpec -> factoriesFileBuilder.addFunction(member)
                }
            }
        }
    }

    context[BindingSlices.NAMESPACE]?.values?.forEach { namespaceDesc ->
        val namespaceObject = namespaceDesc.asNamespacePoet(context, generatedPackageName)
        factoriesFileBuilder.addType(namespaceObject)
    }

    return listOf(apiFileBuilder.build(), factoriesFileBuilder.build())
}