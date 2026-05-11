package com.cdodi.transpiler

import WebIDLBaseVisitor
import WebIDLParser
import kotlin.collections.orEmpty

class SymbolCollectorVisitor(
    private val context: MutableBindingContext,
    private val membersCollector: InterfaceCollector,
    private val typeResolver: TypeResolver,
) : WebIDLBaseVisitor<Unit>() {

    override fun visitMixinRest(ctx: WebIDLParser.MixinRestContext) {
        val name = ctx.IDENTIFIER_WEBIDL()?.text?.trim() ?: return
        val collectedMembers = ctx.mixinMembers()?.let { membersCollector.visit(it) }.orEmpty()
        val descriptor = Descriptor.InterfaceDescriptor(
            name = name,
            members = collectedMembers,
            superTypes = emptySet()
        )
        val maybePresent = context[BindingSlices.MIXIN, name]

        context[BindingSlices.MIXIN, name] = descriptor + maybePresent
    }

    override fun visitPartialInterfaceRest(ctx: WebIDLParser.PartialInterfaceRestContext) {
        val name = ctx.IDENTIFIER_WEBIDL()?.text?.trim() ?: return
        val collectedMembers = ctx.partialInterfaceMembers()?.let { membersCollector.visit(it) }.orEmpty()
        val descriptor = Descriptor.InterfaceDescriptor(
            name = name,
            members = collectedMembers,
            superTypes = emptySet()
        )
        val maybePresent = context[BindingSlices.PARTIAL_INTERFACE, name]

        context[BindingSlices.PARTIAL_INTERFACE, name] = descriptor + maybePresent
    }

    override fun visitInterfaceRest(ctx: WebIDLParser.InterfaceRestContext) {
        val name = ctx.IDENTIFIER_WEBIDL()?.text?.trim() ?: return
        val superTypes = ctx.inheritance()?.IDENTIFIER_WEBIDL()?.text?.trim().let(::setOfNotNull)
        val collectedMembers = ctx.interfaceMembers()?.let { membersCollector.visit(it) }.orEmpty()

        context[BindingSlices.INTERFACE, name] = Descriptor.InterfaceDescriptor(
            name = name,
            members = collectedMembers,
            superTypes = superTypes
        )
    }

    override fun visitIncludesStatement(ctx: WebIDLParser.IncludesStatementContext) {
        val targetName = ctx.IDENTIFIER_WEBIDL(0)?.text ?: return
        val mixinName = ctx.IDENTIFIER_WEBIDL(1)?.text ?: return
        val directive = IncludesDirective(targetName, mixinName)

        context[BindingSlices.INCLUDES, "$targetName+$mixinName"] = directive
    }

    override fun visitNamespace_(ctx: WebIDLParser.Namespace_Context) {
        val name = ctx.IDENTIFIER_WEBIDL()?.text?.trim() ?: return
        val collectedMembers = ctx.namespaceMembers()?.let { membersCollector.visit(it) }.orEmpty()
        context[BindingSlices.NAMESPACE, name] = Descriptor.InterfaceDescriptor(
            name = name,
            members = collectedMembers,
            superTypes = emptySet()
        )
    }

    override fun visitDictionary(ctx: WebIDLParser.DictionaryContext) {
        val name = ctx.IDENTIFIER_WEBIDL()?.text ?: return
        val superTypes = ctx.inheritance()?.IDENTIFIER_WEBIDL()?.text?.trim().let(::setOfNotNull)
        val collectedMembers = ctx.dictionaryMembers()?.let { membersCollector.visit(it) }.orEmpty()

        context[BindingSlices.DICTIONARY, name] = Descriptor.InterfaceDescriptor(
            name = name,
            members = collectedMembers,
            superTypes = superTypes
        )
    }

    override fun visitPartialDictionary(ctx: WebIDLParser.PartialDictionaryContext) {
        val name = ctx.IDENTIFIER_WEBIDL()?.text?.trim() ?: return
        val collectedMembers = ctx.dictionaryMembers()?.let { membersCollector.visit(it) }.orEmpty()
        val descriptor = Descriptor.InterfaceDescriptor(
            name = name,
            members = collectedMembers,
            superTypes = emptySet()
        )
        val maybePresent = context[BindingSlices.PARTIAL_DICTIONARY, name]

        context[BindingSlices.PARTIAL_DICTIONARY, name] = descriptor + maybePresent
    }

    override fun visitEnum_(ctx: WebIDLParser.Enum_Context) {
        val enumName = ctx.IDENTIFIER_WEBIDL()?.text?.trim() ?: return
        val entries = ctx.enumValueList()?.text.orEmpty()
            .split(",")
            .filterNot(predicate = String::isBlank)
            .map { entry -> entry.trim().removeSurrounding(delimiter = "\"") }
        val descriptor = Descriptor.EnumDescriptor(
            name = enumName,
            values = entries
        )

        context[BindingSlices.ENUM, enumName] = descriptor
    }

    override fun visitTypedef_(ctx: WebIDLParser.Typedef_Context) {
        val typeName = ctx.IDENTIFIER_WEBIDL()?.text?.trim() ?: return
        val typeCtx = ctx.typeWithExtendedAttributes() ?: return
        val typeDescriptor = typeResolver.visit(typeCtx)

        context[BindingSlices.TYPEDEF, typeName] = typeDescriptor
    }
}
