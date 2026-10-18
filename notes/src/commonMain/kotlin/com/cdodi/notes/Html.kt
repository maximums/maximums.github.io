package com.cdodi.notes

/** Parsed Markdown as HTML. Headings get ids ([anchorOf]) so sections can be linked. */
object Html {
    fun blocks(blocks: List<Block>): String = buildString {
        for (block in blocks) {
            when (block) {
                is Block.Heading -> append("<h${block.level} id=\"${anchorOf(block)}\">${inlines(block.content)}</h${block.level}>")
                is Block.Paragraph -> append("<p>${inlines(block.content)}</p>")
                is Block.CodeBlock -> {
                    val language = block.language?.let { " class=\"language-${escape(it)}\"" }.orEmpty()
                    append("<pre><code$language>${escape(block.code)}</code></pre>")
                }
                is Block.ListBlock -> {
                    val tag = if (block.ordered) "ol" else "ul"
                    append("<$tag>")
                    block.items.forEach { append("<li>${inlines(it)}</li>") }
                    append("</$tag>")
                }
                is Block.Quote -> append("<blockquote><p>${inlines(block.content)}</p></blockquote>")
                is Block.Table -> {
                    append("<table><thead><tr>")
                    block.header.forEach { append("<th>${inlines(it)}</th>") }
                    append("</tr></thead><tbody>")
                    for (row in block.rows) {
                        append("<tr>")
                        row.forEach { append("<td>${inlines(it)}</td>") }
                        append("</tr>")
                    }
                    append("</tbody></table>")
                }
                Block.Rule -> append("<hr>")
            }
            append('\n')
        }
    }

    fun inlines(inlines: List<Inline>): String = buildString {
        for (inline in inlines) {
            when (inline) {
                is Inline.Text -> append(escape(inline.text))
                is Inline.Code -> append("<code>${escape(inline.code)}</code>")
                is Inline.Emphasis -> append("<em>${inlines(inline.content)}</em>")
                is Inline.Strong -> append("<strong>${inlines(inline.content)}</strong>")
                is Inline.Link -> append("<a href=\"${escape(inline.href)}\">${inlines(inline.content)}</a>")
            }
        }
    }

    /** Text safe in element content and in double-quoted attributes. */
    fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }
}
