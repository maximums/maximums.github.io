package com.cdodi.transpiler

import WebIDLBaseVisitor
import com.cdodi.transpiler.Descriptor.TypeDescriptor

class TypeResolver : WebIDLBaseVisitor<TypeDescriptor>() {
    override fun visitType_(ctx: WebIDLParser.Type_Context): TypeDescriptor {
        val baseType = when {
            ctx.singleType() != null -> visit(ctx.singleType())
            ctx.unionType() != null -> visit(ctx.unionType())
            else -> TypeDescriptor(name = "any")
        }
        val isNullable = ctx.null_()?.text == "?" || ctx.null_()?.text?.isEmpty() == true
        return baseType.copy(isNullable = isNullable)
    }

    override fun visitSingleType(ctx: WebIDLParser.SingleTypeContext): TypeDescriptor {
        ctx.promiseType()?.let { promise ->
            val inner = visit(promise.type_())
            return TypeDescriptor(name = "Promise", promiseOf = inner)
        }

        ctx.distinguishableType()?.let {
            return visitDistinguishableType(it)
        }

        return TypeDescriptor(name = ctx.text)
    }

    override fun visitDistinguishableType(ctx: WebIDLParser.DistinguishableTypeContext): TypeDescriptor {
        if (ctx.getChild(0).text == "sequence") {
            val inner = visit(ctx.typeWithExtendedAttributes())
            return TypeDescriptor(name = "sequence", sequenceOf = inner)
        }

        ctx.recordType()?.let { record ->
            val keyType = TypeDescriptor(name = record.stringType().text)
            val valueType = visit(record.typeWithExtendedAttributes())
            return TypeDescriptor(name = "record", record = mapOf(keyType to valueType))
        }

        return TypeDescriptor(name = ctx.getChild(0).text)
    }

    override fun visitUnionMemberType(ctx: WebIDLParser.UnionMemberTypeContext): TypeDescriptor {
        val dist = ctx.distinguishableType()
        return if (dist != null) {
            val base = visitDistinguishableType(dist)
            val isNullable = ctx.null_()?.text == "?" || ctx.null_()?.text?.isEmpty() == true
            base.copy(isNullable = isNullable)
        } else {
            visit(ctx.unionType())
        }
    }

    override fun visitUnionType(ctx: WebIDLParser.UnionTypeContext): TypeDescriptor {
        val members = mutableListOf<TypeDescriptor>()

        ctx.unionMemberType().forEach { members.add(visitUnionMemberType(it)) }

        var current = ctx.unionMemberTypes()
        while (current != null && current.unionMemberType() != null) {
            members.add(visitUnionMemberType(current.unionMemberType()))
            current = current.unionMemberTypes()
        }

        return TypeDescriptor(name = "union", unionMembers = members)
    }
}
