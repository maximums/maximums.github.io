package com.cdodi.webidl.gradle

import com.cdodi.webidl.CompilerOptions
import com.cdodi.webidl.IdlSource
import com.cdodi.webidl.WebIdlCompiler
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class TranspileWebIdlTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val idlFiles: ConfigurableFileCollection

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val runtimePackage: Property<String>

    @get:Input
    abstract val fileNamePrefix: Property<String>

    @get:Input
    abstract val externalTypes: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    operator fun invoke() {
        val outputDir = outputDirectory.get().asFile.also { dir ->
            if (dir.exists()) dir.deleteRecursively()
            dir.mkdirs()
        }

        val sources = idlFiles.files.map { file -> IdlSource(file.name, file.readText()) }
        val options = CompilerOptions(packageName.get(), runtimePackage.get(), fileNamePrefix.get(), externalTypes.get())

        WebIdlCompiler.compile(sources, options) { warning -> logger.warn(warning) }
            .forEach { fileSpec -> fileSpec.writeTo(outputDir) }
    }
}
