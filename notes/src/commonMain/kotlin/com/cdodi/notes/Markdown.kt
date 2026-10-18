package com.cdodi.notes

/** A block of a note. Lists and quotes hold a single line of inline content each: the notes need no nesting. */
sealed interface Block {
    data class Heading(val level: Int, val content: List<Inline>) : Block

    data class Paragraph(val content: List<Inline>) : Block

    data class CodeBlock(val language: String?, val code: String) : Block

    data class ListBlock(val ordered: Boolean, val items: List<List<Inline>>) : Block

    data class Quote(val content: List<Inline>) : Block

    /** A pipe table: a header row, then the body rows, each a list of cells. */
    data class Table(val header: List<List<Inline>>, val rows: List<List<List<Inline>>>) : Block

    data object Rule : Block
}

sealed interface Inline {
    data class Text(val text: String) : Inline

    data class Code(val code: String) : Inline

    data class Emphasis(val content: List<Inline>) : Inline

    data class Strong(val content: List<Inline>) : Inline

    data class Link(val href: String, val content: List<Inline>) : Inline
}

/**
 * The Markdown the notes are written in. A subset, parsed the same way on every target:
 *
 * - blocks: `#` headings, paragraphs, fenced code with a language, `-`/`*` and `1.` lists (an item continues on
 *   indented lines), `>` quotes, pipe tables with a `|---|` separator, and `---` rules;
 * - inline: `` `code` `` (any run of backticks, closed by the same run), `**strong**`, `*emphasis*`,
 *   `[links](href)` and backslash escapes.
 *
 * Anything else is text, so a stray `*` or `[` shows as itself rather than failing.
 */
object Markdown {
    fun parse(text: String): List<Block> = BlockParser(text.replace("\r\n", "\n").split('\n')).parse()

    fun parseInline(text: String): List<Inline> = InlineParser(text).parse(0, text.length)
}

private val HEADING = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
private val RULE = Regex("""^\s{0,3}(-{3,}|\*{3,})\s*$""")
private val BULLET = Regex("""^\s{0,3}[-*]\s+(.*)$""")
private val NUMBERED = Regex("""^\s{0,3}\d{1,9}\.\s+(.*)$""")
private val TABLE_SEPARATOR = Regex("""^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?\s*$""")

private class BlockParser(private val lines: List<String>) {
    private var at = 0
    private val blocks = mutableListOf<Block>()

    fun parse(): List<Block> {
        while (at < lines.size) {
            val line = lines[at]
            when {
                line.isBlank() -> at++
                line.trimStart().startsWith("```") -> codeBlock()
                HEADING.matches(line) -> heading()
                RULE.matches(line) -> { blocks += Block.Rule; at++ }
                line.trimStart().startsWith(">") -> quote()
                BULLET.matches(line) -> list(BULLET, ordered = false)
                NUMBERED.matches(line) -> list(NUMBERED, ordered = true)
                isTableStart(at) -> table()
                else -> paragraph()
            }
        }
        return blocks
    }

    private fun codeBlock() {
        val fence = lines[at].trimStart()
        val ticks = fence.takeWhile { it == '`' }
        val language = fence.drop(ticks.length).trim().ifEmpty { null }
        val indent = lines[at].length - fence.length
        val code = mutableListOf<String>()
        at++
        while (at < lines.size && !lines[at].trimStart().startsWith(ticks)) {
            code += lines[at].removeIndent(indent)
            at++
        }
        at++ // the closing fence, or past the end when it's missing
        blocks += Block.CodeBlock(language, code.joinToString("\n"))
    }

    private fun heading() {
        val match = HEADING.matchEntire(lines[at])!!
        blocks += Block.Heading(match.groupValues[1].length, Markdown.parseInline(match.groupValues[2]))
        at++
    }

    private fun quote() {
        val text = mutableListOf<String>()
        while (at < lines.size && lines[at].trimStart().startsWith(">")) {
            text += lines[at].trimStart().removePrefix(">").trim()
            at++
        }
        blocks += Block.Quote(Markdown.parseInline(text.joinToString(" ")))
    }

    private fun list(marker: Regex, ordered: Boolean) {
        val items = mutableListOf<String>()
        while (at < lines.size) {
            val line = lines[at]
            val item = marker.matchEntire(line)
            when {
                item != null -> items += item.groupValues[1]
                // An indented line carries on the item above it.
                line.isNotBlank() && line.startsWith("  ") && items.isNotEmpty() -> items[items.lastIndex] += " " + line.trim()
                // A blank line ends the list, unless the next line is another item of it.
                line.isBlank() && at + 1 < lines.size && marker.matches(lines[at + 1]) -> Unit
                else -> break
            }
            at++
        }
        blocks += Block.ListBlock(ordered, items.map { Markdown.parseInline(it.trim()) })
    }

    private fun isTableStart(index: Int): Boolean =
        lines[index].trimStart().startsWith("|") && index + 1 < lines.size && TABLE_SEPARATOR.matches(lines[index + 1])

    private fun table() {
        val header = cells(lines[at])
        at += 2
        val rows = mutableListOf<List<List<Inline>>>()
        while (at < lines.size && lines[at].trimStart().startsWith("|")) {
            rows += cells(lines[at]).let { row -> List(header.size) { row.getOrElse(it) { emptyList() } } }
            at++
        }
        blocks += Block.Table(header, rows)
    }

    /** The cells of a `| a | b |` row. A `|` inside backticks or after a backslash stays in its cell. */
    private fun cells(row: String): List<List<Inline>> {
        val text = row.trim().removePrefix("|").let { if (it.endsWith("|") && !it.endsWith("\\|")) it.dropLast(1) else it }
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var inCode = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && text.getOrNull(i + 1) == '|' -> { cell.append('|'); i++ }
                c == '`' -> { inCode = !inCode; cell.append(c) }
                c == '|' && !inCode -> { cells += cell.toString(); cell.clear() }
                else -> cell.append(c)
            }
            i++
        }
        cells += cell.toString()
        return cells.map { Markdown.parseInline(it.trim()) }
    }

    private fun paragraph() {
        val text = mutableListOf<String>()
        while (at < lines.size && lines[at].isNotBlank() && (text.isEmpty() || !startsBlock(at))) {
            text += lines[at].trim()
            at++
        }
        blocks += Block.Paragraph(Markdown.parseInline(text.joinToString(" ")))
    }

    /** Whether the line at [index] starts a block of its own, ending the paragraph above it. */
    private fun startsBlock(index: Int): Boolean {
        val line = lines[index]
        return line.trimStart().startsWith("```") || HEADING.matches(line) || RULE.matches(line) ||
            line.trimStart().startsWith(">") || BULLET.matches(line) || NUMBERED.matches(line) || isTableStart(index)
    }

    private fun String.removeIndent(indent: Int): String {
        var drop = 0
        while (drop < indent && drop < length && this[drop] == ' ') drop++
        return substring(drop)
    }
}

private class InlineParser(private val text: String) {

    /** The inlines in `text[from, until)`. */
    fun parse(from: Int, until: Int): List<Inline> {
        val result = mutableListOf<Inline>()
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) result += Inline.Text(plain.toString())
            plain.clear()
        }

        var i = from
        while (i < until) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < until && text[i + 1] in ESCAPABLE -> {
                    plain.append(text[i + 1])
                    i += 2
                }
                c == '`' -> {
                    val ticks = run(i, '`', until)
                    val close = findTicks(i + ticks, ticks, until)
                    if (close < 0) {
                        plain.append(text, i, i + ticks)
                        i += ticks
                    } else {
                        flush()
                        result += Inline.Code(text.substring(i + ticks, close).let { code ->
                            if (code.length > 2 && code.startsWith(' ') && code.endsWith(' ')) code.substring(1, code.length - 1) else code
                        })
                        i = close + ticks
                    }
                }
                c == '*' && text.startsWith("**", i) -> {
                    val close = findCloser(i + 2, "**", until)
                    if (close < 0 || close == i + 2) {
                        plain.append("**")
                        i += 2
                    } else {
                        flush()
                        result += Inline.Strong(parse(i + 2, close))
                        i = close + 2
                    }
                }
                c == '*' && i + 1 < until && !text[i + 1].isWhitespace() -> {
                    val close = findCloser(i + 1, "*", until)
                    if (close < 0 || text[close - 1].isWhitespace()) {
                        plain.append(c)
                        i++
                    } else {
                        flush()
                        result += Inline.Emphasis(parse(i + 1, close))
                        i = close + 1
                    }
                }
                c == '[' -> {
                    val link = link(i, until)
                    if (link == null) {
                        plain.append(c)
                        i++
                    } else {
                        flush()
                        result += link.first
                        i = link.second
                    }
                }
                else -> {
                    plain.append(c)
                    i++
                }
            }
        }
        flush()
        return result
    }

    /** A `[text](href)` starting at [open], and the index after it; null when it isn't one. */
    private fun link(open: Int, until: Int): Pair<Inline.Link, Int>? {
        var depth = 0
        var i = open
        while (i < until) {
            when (text[i]) {
                '\\' -> i++
                '`' -> {
                    // A code span in the link text may hold brackets: jump to its last backtick.
                    val ticks = run(i, '`', until)
                    val close = findTicks(i + ticks, ticks, until)
                    i = if (close < 0) i + ticks - 1 else close + ticks - 1
                }
                '[' -> depth++
                ']' -> if (--depth == 0) break
            }
            i++
        }
        val closeBracket = i
        if (closeBracket >= until || text.getOrNull(closeBracket + 1) != '(') return null
        val closeParen = text.indexOf(')', closeBracket + 2).takeIf { it in 0 until until } ?: return null
        val href = text.substring(closeBracket + 2, closeParen).trim()
        if (href.isEmpty() || href.any { it.isWhitespace() }) return null
        return Inline.Link(href, parse(open + 1, closeBracket)) to closeParen + 1
    }

    /** Where [delimiter] closes, from [from], skipping code spans and escapes; -1 if it doesn't. */
    private fun findCloser(from: Int, delimiter: String, until: Int): Int {
        var i = from
        while (i < until) {
            when {
                text[i] == '\\' -> i += 2
                text[i] == '`' -> {
                    val ticks = run(i, '`', until)
                    val close = findTicks(i + ticks, ticks, until)
                    i = if (close < 0) i + ticks else close + ticks
                }
                // "**" is only a closer for "**"; for "*", skip a "**" pair (strong inside emphasis).
                delimiter == "*" && text.startsWith("**", i) -> {
                    val close = findCloser(i + 2, "**", until)
                    i = if (close < 0) i + 2 else close + 2
                }
                text.startsWith(delimiter, i) -> return i
                else -> i++
            }
        }
        return -1
    }

    /** The index of the next run of exactly [count] backticks from [from], or -1. */
    private fun findTicks(from: Int, count: Int, until: Int): Int {
        var i = from
        while (i < until) {
            if (text[i] == '`') {
                val length = run(i, '`', until)
                if (length == count) return i
                i += length
            } else {
                i++
            }
        }
        return -1
    }

    private fun run(from: Int, char: Char, until: Int): Int {
        var i = from
        while (i < until && text[i] == char) i++
        return i - from
    }

    private companion object {
        const val ESCAPABLE = "\\`*_[]()#+-.!|>"
    }
}

/** The text of [inlines] without any markup: for word counts, ids and plain-text summaries. */
fun plainText(inlines: List<Inline>): String = buildString {
    for (inline in inlines) {
        when (inline) {
            is Inline.Text -> append(inline.text)
            is Inline.Code -> append(inline.code)
            is Inline.Emphasis -> append(plainText(inline.content))
            is Inline.Strong -> append(plainText(inline.content))
            is Inline.Link -> append(plainText(inline.content))
        }
    }
}

/** A heading's anchor: lower case, letters and digits, words joined by hyphens. */
fun anchorOf(heading: Block.Heading): String =
    plainText(heading.content).lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.joinToString("-")
