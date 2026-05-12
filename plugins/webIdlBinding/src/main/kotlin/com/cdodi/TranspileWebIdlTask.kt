package com.cdodi

import WebIDLLexer
import WebIDLParser
import com.cdodi.transpiler.InterfaceCollector
import com.cdodi.transpiler.SymbolCollectorVisitor
import com.cdodi.transpiler.MutableBindingContext
import com.cdodi.transpiler.TypeResolver
import com.cdodi.transpiler.generateKotlin
import com.cdodi.transpiler.resolveSemantics
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class TranspileWebIdlTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val idlFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    operator fun invoke() {
        val idlFile = idlFile.get().asFile
        val outputDir = outputDirectory.get().asFile.also { dir ->
            if (dir.exists()) dir.deleteRecursively()
            dir.mkdirs()
        }

        val errorListener = FailFastErrorListener()
        val lexer = WebIDLLexer(CharStreams.fromFileName(idlFile.absolutePath)).apply {
            removeErrorListeners()
            addErrorListener(errorListener)
        }
        val tokens = CommonTokenStream(lexer)
        val parser = WebIDLParser(tokens).apply {
            removeErrorListeners()
            addErrorListener(errorListener)
        }
        val tree = parser.webIDL()
        val collectionContext = MutableBindingContext()

        val typeResolver = TypeResolver()
        val membersCollector = InterfaceCollector(typeResolver) { msg -> logger.warn(msg) }
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

private class FailFastErrorListener : BaseErrorListener() {
    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String?,
        e: RecognitionException?
    ) {
        throw IllegalStateException("WebIDL parse error at line $line:$charPositionInLine — $msg")
    }
}