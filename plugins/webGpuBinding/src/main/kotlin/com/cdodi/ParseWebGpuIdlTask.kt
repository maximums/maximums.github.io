package com.cdodi

import WebIDLLexer
import WebIDLParser
import com.cdodi.transpiler.InterfaceCollector
import com.cdodi.transpiler.SymbolCollectorVisitor
import com.cdodi.transpiler.MutableBindingContext
import com.cdodi.transpiler.TypeResolver
import com.cdodi.transpiler.generateKotlin
import com.cdodi.transpiler.resolveSemantics
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class ParseWebGpuIdlTask : DefaultTask() {

    @get:InputFile
    abstract val webGpuIdlFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    operator fun invoke() {
        val idlFile = webGpuIdlFile.get().asFile
        val outputDir = outputDirectory.get().asFile.also { dir ->
            if (dir.exists()) dir.deleteRecursively()
            dir.mkdirs()
        }

        val lexer = WebIDLLexer(CharStreams.fromFileName(idlFile.absolutePath))
        val tokens = CommonTokenStream(lexer)
        val parser = WebIDLParser(tokens)
        val tree = parser.webIDL()
        val collectionContext = MutableBindingContext()

        val typeResolver = TypeResolver()
        val membersCollector = InterfaceCollector(typeResolver)
        SymbolCollectorVisitor(collectionContext, membersCollector, typeResolver).also { it.visit(tree) }
        val resolvedContext = resolveSemantics(collectionContext)

        val fileSpecs = generateKotlin(
            resolvedContext,
            "com.cdodi.webgpu.bindings",
            "WebGpuBindings",
            "WebGpuFactories",
        )
        fileSpecs.forEach { fileSpec -> fileSpec.writeTo(outputDir) }
    }
}