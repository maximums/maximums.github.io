package com.cdodi.notes

import kotlin.math.ceil

/**
 * A write-up: a Markdown file with front matter.
 *
 * ```
 * ---
 * title: I built a WebIDL compiler
 * date: 2026-10-18
 * summary: One or two sentences for the list, the description and link previews.
 * ---
 * The body, in [Markdown].
 * ```
 *
 * [slug] is the file name without `.md`: it names the note in URLs.
 */
data class Note(val slug: String, val title: String, val date: String, val summary: String, val blocks: List<Block>) {

    /** Minutes to read, at 220 words a minute for prose and half that for code. */
    val readingMinutes: Int
        get() {
            var words = 0.0
            for (block in blocks) {
                words += when (block) {
                    is Block.CodeBlock -> wordsIn(block.code) * 2.0
                    is Block.Heading -> wordsIn(plainText(block.content)).toDouble()
                    is Block.Paragraph -> wordsIn(plainText(block.content)).toDouble()
                    is Block.Quote -> wordsIn(plainText(block.content)).toDouble()
                    is Block.ListBlock -> block.items.sumOf { wordsIn(plainText(it)) }.toDouble()
                    is Block.Table -> (block.rows + listOf(block.header)).sumOf { row -> row.sumOf { wordsIn(plainText(it)) } }.toDouble()
                    Block.Rule -> 0.0
                }
            }
            return ceil(words / WORDS_PER_MINUTE).toInt().coerceAtLeast(1)
        }

    companion object {
        private const val WORDS_PER_MINUTE = 220.0
        private val REQUIRED = listOf("title", "date", "summary")
        private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

        /** Parses the note named [slug]. Fails with the slug in the message when the front matter is incomplete. */
        fun parse(slug: String, source: String): Note {
            val lines = source.replace("\r\n", "\n").split('\n')
            require(lines.firstOrNull()?.trim() == "---") { "$slug: a note starts with front matter between --- lines" }
            val end = lines.drop(1).indexOfFirst { it.trim() == "---" }.takeIf { it >= 0 }?.plus(1)
                ?: throw IllegalArgumentException("$slug: the front matter has no closing ---")
            val fields = lines.subList(1, end).filter { it.isNotBlank() }.associate { line ->
                val key = line.substringBefore(':', missingDelimiterValue = "").trim()
                require(key.isNotEmpty()) { "$slug: front matter lines are `key: value`, not \"$line\"" }
                // A value may be quoted, as in YAML, when it holds a colon of its own.
                key to line.substringAfter(':').trim().let { if (it.length >= 2 && it.startsWith('"') && it.endsWith('"')) it.substring(1, it.length - 1) else it }
            }
            val missing = REQUIRED.filter { fields[it].isNullOrEmpty() }
            require(missing.isEmpty()) { "$slug: the front matter needs ${missing.joinToString()}" }
            val date = fields.getValue("date")
            require(DATE.matches(date)) { "$slug: the date is YYYY-MM-DD, not $date" }
            val body = Markdown.parse(lines.drop(end + 1).joinToString("\n"))
            return Note(slug, fields.getValue("title"), date, fields.getValue("summary"), body)
        }

        /** The slugs listed in `index.txt`, in order: one per line, `#` starts a comment. */
        fun index(source: String): List<String> =
            source.split('\n').map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }

        private fun wordsIn(text: String): Int = text.split(Regex("""\s+""")).count { it.isNotEmpty() }
    }
}

/** "18 October 2026" for 2026-10-18. */
fun formatDate(date: String): String {
    val (year, month, day) = date.split('-')
    return "${day.toInt()} ${MONTHS[month.toInt() - 1]} $year"
}

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December",
)
