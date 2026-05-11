@file:Suppress("unused")

package com.cdodi

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class WebIdlBindingsPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            val downloadTask = tasks.register<DownloadIdlTask>("downloadIdl") {
                group = TASKS_GROUP
                idlUrl = providers.gradleProperty("webIdlUrl")
                idlFile = layout.buildDirectory.file("webidl/spec.idl")
            }

            val transpileTask = tasks.register<TranspileWebIdlTask>("transpileWebIdl") {
                group = TASKS_GROUP
                idlFile = downloadTask.flatMap { it.idlFile }
                outputDirectory = layout.buildDirectory.dir("webidl-src")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.wasmJsMain.configure {
                    kotlin.srcDir(transpileTask.flatMap { it.outputDirectory })
                }
            }
        }

        logger.info("WebIdlBindings plugin applied.")
    }

    private companion object {
        const val TASKS_GROUP = "webidl"
    }
}
