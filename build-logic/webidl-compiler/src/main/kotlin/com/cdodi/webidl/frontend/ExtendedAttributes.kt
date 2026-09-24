package com.cdodi.webidl.frontend

import com.cdodi.webidl.parser.WebIDLParser
import org.antlr.v4.runtime.ParserRuleContext

/**
 * `[Exposed=(Window, Worker), SecureContext]` -> `["Exposed=(Window,Worker)", "SecureContext"]`.
 * The parse tree's text has no whitespace; commas inside parentheses belong to one attribute.
 */
internal fun WebIDLParser.ExtendedAttributeListContext?.attributes(): List<String> {
    val text = this?.text?.removePrefix("[")?.removeSuffix("]").orEmpty()
    if (text.isBlank()) return emptyList()

    val attributes = mutableListOf<String>()
    var depth = 0
    var start = 0
    text.forEachIndexed { index, char ->
        when (char) {
            '(', '[', '{' -> depth++
            ')', ']', '}' -> depth--
            ',' -> if (depth == 0) {
                attributes += text.substring(start, index)
                start = index + 1
            }
        }
    }
    attributes += text.substring(start)
    return attributes.filter(String::isNotBlank)
}

/**
 * In this grammar a member, argument or definition is always preceded by its `[...]` list in the parent rule
 * (`interfaceMembers: extendedAttributeList interfaceMember ...`, `argument: extendedAttributeList argumentRest`, ...).
 * Walks up a few levels, because the node visited is sometimes nested inside the rule that has the list as sibling.
 */
internal fun ParserRuleContext.precedingExtendedAttributes(maxDepth: Int = 5): List<String> {
    var node: ParserRuleContext = this
    repeat(maxDepth) {
        val parent = node.parent as? ParserRuleContext ?: return emptyList()
        val index = parent.children.indexOf(node)
        val previous = parent.children.getOrNull(index - 1)
        if (previous is WebIDLParser.ExtendedAttributeListContext) return previous.attributes()
        node = parent
    }
    return emptyList()
}
