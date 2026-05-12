package com.cdodi.transpiler

import WebIDLBaseVisitor
import WebIDLParser
import org.antlr.v4.runtime.RuleContext

class InterfaceCollector(
    private val typeResolver: TypeResolver,
    private val onUnsupported: (String) -> Unit = {},
) : WebIDLBaseVisitor<List<InterfaceMember>>() {
    override fun defaultResult(): List<InterfaceMember> = emptyList()

    override fun aggregateResult(aggregate: List<InterfaceMember>, nextResult: List<InterfaceMember>) = aggregate + nextResult

    override fun visitPartialInterfaceMember(ctx: WebIDLParser.PartialInterfaceMemberContext): List<InterfaceMember> {
        ctx.readonlyMember()?.readonlyMemberRest()?.attributeRest()?.extractVariable(isReadonly = true)?.let { return it }
        ctx.readWriteAttribute()?.attributeRest()?.extractVariable()?.let { return it }
        ctx.operation()?.regularOperation()?.extractFunction()?.let { return it }

        val text = ctx.text.trim()
        if (text.isNotEmpty()) onUnsupported("Skipping unsupported partial interface member: $text")
        return super.visitPartialInterfaceMember(ctx)
    }

    override fun visitMixinMember(ctx: WebIDLParser.MixinMemberContext): List<InterfaceMember> {
        ctx.attributeRest()?.extractVariable()?.let { return it }
        ctx.regularOperation()?.extractFunction()?.let { return it }

        val text = ctx.text.trim()
        if (text.isNotEmpty()) onUnsupported("Skipping unsupported mixin member: $text")
        return super.visitMixinMember(ctx)
    }

    override fun visitInterfaceMember(ctx: WebIDLParser.InterfaceMemberContext): List<InterfaceMember> {
        ctx.constructor()?.let { constructorCtx ->
            val parameters = constructorCtx.argumentList()?.extractArguments().orEmpty()

            var parentNode: RuleContext? = ctx.parent
            var interfaceName = "Unknown"
            while (parentNode != null) {
                if (parentNode is WebIDLParser.InterfaceRestContext) {
                    interfaceName = parentNode.IDENTIFIER_WEBIDL()?.text?.trim() ?: "Unknown"
                    break
                }
                parentNode = parentNode.parent
            }

            return listOf(
                InterfaceMember.FunctionDescriptor(
                    name = "constructor",
                    returnType = Descriptor.TypeDescriptor(name = interfaceName, isNullable = false),
                    parameters = parameters,
                )
            )
        }

        return super.visitInterfaceMember(ctx)
    }

    override fun visitConst_(ctx: WebIDLParser.Const_Context): List<InterfaceMember> {
        val name = ctx.IDENTIFIER_WEBIDL()?.text?.trim() ?: return super.visitConst_(ctx)
        val constType = ctx.constType() ?: return super.visitConst_(ctx)
        val typeName = constType.primitiveType()?.text ?: constType.IDENTIFIER_WEBIDL()?.text?.trim()
            ?: return super.visitConst_(ctx)
        val type = Descriptor.TypeDescriptor(name = typeName, isNullable = false)
        val value = ctx.constValue()?.text?.trim() ?: return super.visitConst_(ctx)
        return listOf(InterfaceMember.ConstantDescriptor(name = name, type = type, value = value))
    }

    override fun visitDictionaryMemberRest(ctx: WebIDLParser.DictionaryMemberRestContext): List<InterfaceMember> {
        val name = ctx.IDENTIFIER_WEBIDL()?.text?.trim() ?: return super.visitDictionaryMemberRest(ctx)
        val typeCtx = ctx.typeWithExtendedAttributes() ?: ctx.type_()
        val type = typeCtx?.let { typeResolver.visit(it) } ?: return super.visitDictionaryMemberRest(ctx)
        val defaultValue = ctx.default_()?.cleanDefValue
        val isRequired = ctx.getChild(0)?.text == "required"

        return listOf(
            InterfaceMember.VariableDescriptor(
                name = name,
                type = type,
                isRequired = isRequired,
                defaultValue = defaultValue
            )
        )
    }

    private fun WebIDLParser.AttributeRestContext.extractVariable(isReadonly: Boolean = false): List<InterfaceMember>? {
        val attrName = attributeName()?.IDENTIFIER_WEBIDL()?.text?.trim() ?: return null
        val attrType = typeWithExtendedAttributes()?.let { typeResolver.visit(it) } ?: return null

        return listOf(InterfaceMember.VariableDescriptor(name = attrName, type = attrType, isReadonly = isReadonly))
    }

    private fun WebIDLParser.RegularOperationContext.extractFunction(): List<InterfaceMember>? {
        val returnType = type_()?.let { typeResolver.visit(it) } ?: return null
        val funName = operationRest()?.optionalOperationName()?.operationName()
            ?.IDENTIFIER_WEBIDL()?.text?.trim() ?: return null

        val parameters = operationRest()?.argumentList()?.extractArguments().orEmpty()

        return listOf(
            InterfaceMember.FunctionDescriptor(
                name = funName,
                returnType = returnType,
                parameters = parameters
            )
        )
    }

    private fun WebIDLParser.ArgumentListContext.extractArguments(): List<InterfaceMember.VariableDescriptor> {
        val collectedArgs = mutableListOf<InterfaceMember.VariableDescriptor>()

        argument()?.argumentRest()?.toAstMember()?.let(collectedArgs::add)

        var argsCtx = arguments()
        while (argsCtx != null) {
            argsCtx.argument()?.argumentRest()?.toAstMember()?.let(collectedArgs::add)
            argsCtx = argsCtx.arguments()
        }

        return collectedArgs
    }

    private fun WebIDLParser.ArgumentRestContext.toAstMember(): InterfaceMember.VariableDescriptor? {
        val typeCtx = type_() ?: typeWithExtendedAttributes()
        val type = typeCtx?.let { typeResolver.visit(it) } ?: return null

        return InterfaceMember.VariableDescriptor(
            name = argumentName()?.IDENTIFIER_WEBIDL()?.text?.trim().orEmpty(),
            type = type,
            defaultValue = default_()?.cleanDefValue
        )
    }

    private val WebIDLParser.Default_Context.cleanDefValue: String?
        get() {
            val rawText = defaultValue()?.text?.trim() ?: return null
            return rawText.removeSurrounding("\"")
        }
}
