package com.cdodi.webidl

import com.cdodi.webidl.backend.generateKotlin
import com.cdodi.webidl.frontend.InterfaceCollector
import com.cdodi.webidl.frontend.SymbolCollectorVisitor
import com.cdodi.webidl.frontend.TypeResolver
import com.cdodi.webidl.model.ExternalType
import com.cdodi.webidl.model.MutableBindingContext
import com.cdodi.webidl.parser.WebIDLLexer
import com.cdodi.webidl.parser.WebIDLParser
import com.cdodi.webidl.passes.resolveSemantics
import com.squareup.kotlinpoet.FileSpec
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer

/** One WebIDL document, e.g. the contents of `webgpu.idl`. */
data class IdlSource(val name: String, val text: String)

data class CompilerOptions(
    val packageName: String,
    val runtimePackage: String,
    /** Prefix for the generated file names: "WebGpu" -> WebGpuEnums.kt, WebGpuInterfaces.kt, ... */
    val fileNamePrefix: String = "",
    /** Base URL of the spec, for links in KDoc (e.g. https://gpuweb.github.io/gpuweb/); null means no links. */
    val specUrl: String? = null,
    /** IDL name -> `"class|interface|value <Kotlin type>"`, see [ExternalType.parse]. */
    val externalTypes: Map<String, String> = ExternalType.DEFAULTS,
)

/**
 * The whole pipeline — parse, collect symbols, resolve, generate — without Gradle,
 * so the Gradle task and the tests run exactly the same code.
 */
object WebIdlCompiler {

    fun compile(
        sources: List<IdlSource>,
        options: CompilerOptions,
        onWarning: (String) -> Unit = {},
    ): List<FileSpec> {
        val collectionContext = MutableBindingContext()
        val typeResolver = TypeResolver()
        val membersCollector = InterfaceCollector(typeResolver, onWarning)
        val symbolCollector = SymbolCollectorVisitor(collectionContext, membersCollector, typeResolver)

        sources.sortedBy(IdlSource::name).forEach { source -> symbolCollector.visit(parse(source)) }

        return generateKotlin(
            resolveSemantics(collectionContext, ExternalType.parseAll(options.externalTypes)),
            options.packageName,
            options.runtimePackage,
            options.fileNamePrefix,
            options.specUrl,
        )
    }

    private fun parse(source: IdlSource): WebIDLParser.WebIDLContext {
        val errorListener = FailFastErrorListener(source.name)
        val lexer = WebIDLLexer(CharStreams.fromString(source.text, source.name)).apply {
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

private class FailFastErrorListener(private val sourceName: String) : BaseErrorListener() {
    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String?,
        e: RecognitionException?
    ) {
        throw IllegalStateException("WebIDL parse error in $sourceName at $line:$charPositionInLine — $msg")
    }
}
