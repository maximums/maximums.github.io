@file:Suppress("unused")

package com.cdodi.webidl.gradle

import com.cdodi.webidl.model.ExternalType
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class WebIdlPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val extension = extensions.create<WebIdlExtension>("webIdl").apply {
            apiFileName.convention("Bindings")
            factoriesFileName.convention("Factories")
            externalTypes.convention(ExternalType.DEFAULTS)
        }

        val transpileTask = tasks.register<TranspileWebIdlTask>("transpileWebIdl") {
            group = TASKS_GROUP
            description = "Generates Kotlin/Wasm declarations from the committed WebIDL files."
            idlFiles.from(extension.idlFiles)
            packageName = extension.packageName
            runtimePackage = extension.runtimePackage
            apiFileName = extension.apiFileName
            factoriesFileName = extension.factoriesFileName
            externalTypes = extension.externalTypes
            outputDirectory = layout.buildDirectory.dir("generated/webidl/kotlin")
        }

        tasks.register<UpdateWebIdlTask>("updateWebIdl") {
            group = TASKS_GROUP
            description = "Run by hand: replaces the committed WebIDL files with a pinned @webref/idl version (-PwebrefVersion=x.y.z)."
            idlFiles.from(extension.idlFiles)
            webrefVersion = providers.gradleProperty("webrefVersion")
        }

        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.wasmJsMain.configure {
                    kotlin.srcDir(transpileTask.flatMap { it.outputDirectory })
                }
            }
        }
    }

    private companion object {
        const val TASKS_GROUP = "webidl"
    }
}
