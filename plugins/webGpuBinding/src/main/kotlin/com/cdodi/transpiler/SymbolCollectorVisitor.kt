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
            superTypes = emptySet() // Mixin doesn't support inheritance
        )
        val maybePresent = context[BindingSlices.FAKE_INTERFACE, name] // Actually I don't think it is possible, but I am tired already

        context[BindingSlices.FAKE_INTERFACE, name] = descriptor + maybePresent
    }

    override fun visitPartialInterfaceRest(ctx: WebIDLParser.PartialInterfaceRestContext) {
        val name = ctx.IDENTIFIER_WEBIDL()?.text?.trim() ?: return
        val collectedMembers = ctx.partialInterfaceMembers()?.let { membersCollector.visit(it) }.orEmpty()
        val descriptor = Descriptor.InterfaceDescriptor(
            name = name,
            members = collectedMembers,
            superTypes = emptySet() // Partial interfaces doesn't support inheritance either
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

    // Questionable -- ii posibil ca sa se execute inainte ca `mixin` sau `interface` sau fie adaugat in `context`??
    override fun visitIncludesStatement(ctx: WebIDLParser.IncludesStatementContext) {
        val targetClassName = ctx.IDENTIFIER_WEBIDL(0)?.text ?: return
        val mixinName = ctx.IDENTIFIER_WEBIDL(1)?.text ?: return
        val targetClass = context[BindingSlices.INTERFACE, targetClassName] ?: return
        val mixin = context[BindingSlices.FAKE_INTERFACE, mixinName]
        val result = targetClass + mixin

        context[BindingSlices.INTERFACE, targetClassName] = result
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
            superTypes = emptySet() // Partial dictionaries doesn't support inheritance either
        )
        val maybePresent = context[BindingSlices.PARTIAL_DICTIONARY, name]

        context[BindingSlices.PARTIAL_DICTIONARY, name] = descriptor + maybePresent
    }

    // Whatever, it is good enough
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
