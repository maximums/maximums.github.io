package com.cdodi.webidl.frontend

import com.cdodi.webidl.model.Descriptor
import com.cdodi.webidl.model.InterfaceMember
import com.cdodi.webidl.parser.WebIDLBaseVisitor
import com.cdodi.webidl.parser.WebIDLParser
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
        ctx.readonlyMember()?.readonlyMemberRest()?.setlikeRest()?.extractSetlike(isReadonly = true)?.let { return it }
        ctx.readWriteSetlike()?.setlikeRest()?.extractSetlike(isReadonly = false)?.let { return it }

        val text = ctx.text.trim()
        if (text.isNotEmpty()) onUnsupported("Skipping unsupported partial interface member: $text")
        return super.visitPartialInterfaceMember(ctx)
    }

    override fun visitMixinMember(ctx: WebIDLParser.MixinMemberContext): List<InterfaceMember> {
        ctx.attributeRest()?.extractVariable(isReadonly = ctx.optionalReadOnly()?.text == "readonly")?.let { return it }
        ctx.regularOperation()?.extractFunction()?.let { return it }

        val text = ctx.text.trim()
        if (text.isNotEmpty()) onUnsupported("Skipping unsupported mixin member: $text")
        return super.visitMixinMember(ctx)
    }

    override fun visitInterfaceMember(ctx: WebIDLParser.InterfaceMemberContext): List<InterfaceMember> {
        ctx.constructor()?.let { constructorCtx ->
            val parameters = constructorCtx.argumentList()?.extractArguments().orEmpty()

            return listOf(
                InterfaceMember.FunctionDescriptor(
                    name = "constructor",
                    returnType = Descriptor.TypeDescriptor(name = ctx.enclosingInterfaceName()),
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

    /**
     * `setlike<T>` declares the members of a JS Set (WebIDL "setlike declarations"): `size` and `has()`, plus `add()`,
     * `delete()` and `clear()` unless it is readonly. forEach and the iterators need callback and iterator types that
     * the model cannot express yet, so they are left out.
     */
    private fun WebIDLParser.SetlikeRestContext.extractSetlike(isReadonly: Boolean): List<InterfaceMember>? {
        val element = typeWithExtendedAttributes()?.let { typeResolver.visit(it) } ?: return null
        val value = listOf(InterfaceMember.VariableDescriptor(name = "value", type = element))
        val boolean = Descriptor.TypeDescriptor(name = "boolean")

        val members = mutableListOf<InterfaceMember>(
            InterfaceMember.VariableDescriptor(name = "size", type = Descriptor.TypeDescriptor(name = "unsignedlong"), isReadonly = true),
            InterfaceMember.FunctionDescriptor(name = "has", returnType = boolean, parameters = value),
        )
        if (!isReadonly) {
            members += InterfaceMember.FunctionDescriptor(name = "add", returnType = Descriptor.TypeDescriptor(name = enclosingInterfaceName()), parameters = value)
            members += InterfaceMember.FunctionDescriptor(name = "delete", returnType = boolean, parameters = value)
            members += InterfaceMember.FunctionDescriptor(name = "clear", returnType = Descriptor.TypeDescriptor(name = "undefined"), parameters = emptyList())
        }
        return members
    }

    private fun RuleContext.enclosingInterfaceName(): String {
        var node: RuleContext? = parent
        while (node != null) {
            if (node is WebIDLParser.InterfaceRestContext) {
                return node.IDENTIFIER_WEBIDL()?.text?.trim() ?: error("Interface without a name")
            }
            node = node.parent
        }
        error("Member outside of an interface at ${(this as? org.antlr.v4.runtime.ParserRuleContext)?.start?.line}")
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
            isOptional = getChild(0)?.text == "optional",
            isVariadic = ellipsis()?.text == "...",
            defaultValue = default_()?.cleanDefValue
        )
    }

    private val WebIDLParser.Default_Context.cleanDefValue: String?
        get() {
            val rawText = defaultValue()?.text?.trim() ?: return null
            return rawText.removeSurrounding("\"")
        }
}
