package com.cdodi.webidl.gradle

import com.cdodi.webidl.backend.generateKotlin
import com.cdodi.webidl.frontend.InterfaceCollector
import com.cdodi.webidl.frontend.SymbolCollectorVisitor
import com.cdodi.webidl.frontend.TypeResolver
import com.cdodi.webidl.model.MutableBindingContext
import com.cdodi.webidl.parser.WebIDLLexer
import com.cdodi.webidl.parser.WebIDLParser
import com.cdodi.webidl.passes.resolveSemantics
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

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
    abstract val apiFileName: Property<String>

    @get:Input
    abstract val factoriesFileName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    operator fun invoke() {
        val outputDir = outputDirectory.get().asFile.also { dir ->
            if (dir.exists()) dir.deleteRecursively()
            dir.mkdirs()
        }

        val collectionContext = MutableBindingContext()
        val typeResolver = TypeResolver()
        val membersCollector = InterfaceCollector(typeResolver) { msg -> logger.warn(msg) }
        val symbolCollector = SymbolCollectorVisitor(collectionContext, membersCollector, typeResolver)

        idlFiles.files.sortedBy(File::getName).forEach { idlFile -> symbolCollector.visit(parse(idlFile)) }
        val resolvedContext = resolveSemantics(collectionContext)

        val fileSpecs = generateKotlin(
            resolvedContext,
            packageName.get(),
            runtimePackage.get(),
            apiFileName.get(),
            factoriesFileName.get(),
        )
        fileSpecs.forEach { fileSpec -> fileSpec.writeTo(outputDir) }
    }

    private fun parse(idlFile: File): WebIDLParser.WebIDLContext {
        val errorListener = FailFastErrorListener(idlFile.name)
        val lexer = WebIDLLexer(CharStreams.fromFileName(idlFile.absolutePath)).apply {
            removeErrorListeners()
            addErrorListener(errorListener)
        }
        val parser = WebIDLParser(CommonTokenStream(lexer)).apply {
            removeErrorListeners()
            addErrorListener(errorListener)
        }
        return parser.webIDL()
    }
}

private class FailFastErrorListener(private val fileName: String) : BaseErrorListener() {
    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String?,
        e: RecognitionException?
    ) {
        throw IllegalStateException("WebIDL parse error in $fileName at $line:$charPositionInLine — $msg")
    }
}