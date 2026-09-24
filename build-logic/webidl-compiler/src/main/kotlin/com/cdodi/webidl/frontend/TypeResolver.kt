package com.cdodi.webidl.frontend

import com.cdodi.webidl.model.Descriptor.TypeDescriptor
import com.cdodi.webidl.parser.WebIDLBaseVisitor
import com.cdodi.webidl.parser.WebIDLParser

class TypeResolver : WebIDLBaseVisitor<TypeDescriptor>() {
    // The grammar puts `?` inside distinguishableType, and after a union in type_ and unionMemberType;
    // each rule reads its own marker.
    override fun visitType_(ctx: WebIDLParser.Type_Context): TypeDescriptor = when {
        ctx.singleType() != null -> visit(ctx.singleType())
        ctx.unionType() != null -> visit(ctx.unionType()).copy(isNullable = ctx.null_().isMarked)
        else -> TypeDescriptor(name = "any", isNullable = true)
    }

    override fun visitSingleType(ctx: WebIDLParser.SingleTypeContext): TypeDescriptor {
        ctx.promiseType()?.let { promise ->
            val inner = visit(promise.type_())
            return TypeDescriptor(name = "Promise", promiseOf = inner)
        }

        ctx.distinguishableType()?.let {
            return visitDistinguishableType(it)
        }

        val rawName = ctx.text.trim()
        check(rawName.isNotEmpty() && rawName.all { it.isLetterOrDigit() || it == '_' }) {
            "Unable to resolve type from: '${ctx.text}' at ${ctx.start.line}:${ctx.start.charPositionInLine}"
        }
        return TypeDescriptor(name = rawName)
    }

    override fun visitDistinguishableType(ctx: WebIDLParser.DistinguishableTypeContext): TypeDescriptor {
        val isNullable = ctx.null_().isMarked
        val firstChild = ctx.getChild(0).text
        if (firstChild == "sequence" || firstChild == "FrozenArray" || firstChild == "ObservableArray") {
            val inner = visit(ctx.typeWithExtendedAttributes())
            return TypeDescriptor(name = "sequence", sequenceOf = inner, isNullable = isNullable)
        }

        ctx.recordType()?.let { record ->
            val keyType = TypeDescriptor(name = record.stringType().text)
            val valueType = visit(record.typeWithExtendedAttributes())
            return TypeDescriptor(name = "record", record = mapOf(keyType to valueType), isNullable = isNullable)
        }

        return TypeDescriptor(name = firstChild, isNullable = isNullable)
    }

    override fun visitUnionMemberType(ctx: WebIDLParser.UnionMemberTypeContext): TypeDescriptor {
        val dist = ctx.distinguishableType()
        return if (dist != null) {
            visitDistinguishableType(dist)
        } else {
            visit(ctx.unionType()).copy(isNullable = ctx.null_().isMarked)
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

    private val WebIDLParser.Null_Context?.isMarked: Boolean
        get() = this?.text == "?"
}
