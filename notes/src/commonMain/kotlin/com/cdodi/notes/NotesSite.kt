package com.cdodi.notes

/**
 * The notes as a plain website: a page per note, an index, a sitemap and `robots.txt`, as paths relative to the site
 * root mapped to their contents.
 *
 * The site itself is drawn on a canvas, which search engines, link previews and screen readers can't read. These
 * pages are what they get instead: real HTML with a description, Open Graph tags and structured data, in the site's
 * colours and type (DESIGN.md), each linking to the same note on the live site.
 */
class NotesSite(private val siteUrl: String, private val author: String) {

    fun pages(notes: List<Note>): Map<String, String> = buildMap {
        put("notes/index.html", index(notes))
        notes.forEach { put("notes/${it.slug}.html", page(it)) }
        put("sitemap.xml", sitemap(notes))
        put("robots.txt", "User-agent: *\nAllow: /\nSitemap: $siteUrl/sitemap.xml\n")
    }

    private fun page(note: Note): String {
        val url = "$siteUrl/notes/${note.slug}.html"
        val structuredData = json(
            "@context" to "https://schema.org",
            "@type" to "BlogPosting",
            "headline" to note.title,
            "description" to note.summary,
            "datePublished" to note.date,
            "url" to url,
            "author" to mapOf("@type" to "Person", "name" to author, "url" to "$siteUrl/"),
        )
        return document(
            title = "${note.title} · $author",
            description = note.summary,
            url = url,
            type = "article",
            extraHead = """
                |<meta property="article:published_time" content="${note.date}">
                |<script type="application/ld+json">$structuredData</script>
            """.trimMargin(),
            body = """
                |<article>
                |<p class="meta">${formatDate(note.date)} · ${note.readingMinutes} min read</p>
                |<h1>${Html.escape(note.title)}</h1>
                |${Html.blocks(note.blocks)}
                |</article>
                |<p class="after"><a href="/#/notes?note=${note.slug}">Read it on the glass</a>, with the rain behind it · <a href="/notes/">All notes</a></p>
            """.trimMargin(),
        )
    }

    private fun index(notes: List<Note>): String = document(
        title = "Notes · $author",
        description = "Write-ups on how the site is built: a WebIDL compiler for type-safe WebGPU in Kotlin/Wasm, " +
            "boids on compute shaders, and navigation where the edges are the transitions.",
        url = "$siteUrl/notes/",
        type = "website",
        extraHead = "",
        body = buildString {
            append("<h1>Notes</h1>\n<ul class=\"notes\">\n")
            for (note in notes) {
                append("<li><p class=\"meta\">${formatDate(note.date)} · ${note.readingMinutes} min read</p>")
                append("<a class=\"title\" href=\"/notes/${note.slug}.html\">${Html.escape(note.title)}</a>")
                append("<p>${Html.escape(note.summary)}</p></li>\n")
            }
            append("</ul>")
        },
    )

    private fun sitemap(notes: List<Note>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n")
        append("<url><loc>$siteUrl/</loc></url>\n")
        append("<url><loc>$siteUrl/notes/</loc>${notes.maxOfOrNull { it.date }?.let { "<lastmod>$it</lastmod>" }.orEmpty()}</url>\n")
        notes.forEach { append("<url><loc>$siteUrl/notes/${it.slug}.html</loc><lastmod>${it.date}</lastmod></url>\n") }
        append("</urlset>\n")
    }

    private fun document(title: String, description: String, url: String, type: String, extraHead: String, body: String) = """
        |<!DOCTYPE html>
        |<html lang="en">
        |<head>
        |<meta charset="UTF-8">
        |<meta name="viewport" content="width=device-width, initial-scale=1">
        |<title>${Html.escape(title)}</title>
        |<meta name="description" content="${Html.escape(description)}">
        |<link rel="canonical" href="$url">
        |<meta property="og:type" content="$type">
        |<meta property="og:site_name" content="${Html.escape(author)}">
        |<meta property="og:title" content="${Html.escape(title)}">
        |<meta property="og:description" content="${Html.escape(description)}">
        |<meta property="og:url" content="$url">
        |<meta property="og:image" content="$siteUrl/og.png">
        |<meta name="twitter:card" content="summary_large_image">
        |<meta name="theme-color" content="#0D1013">
        |<link rel="icon" href="/favicon.svg" type="image/svg+xml">
        |<link rel="preconnect" href="https://fonts.googleapis.com">
        |<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        |<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Cormorant+Garamond:ital,wght@1,300;1,400&family=JetBrains+Mono&family=Manrope:wght@400;600&display=swap">
        |<style>$STYLE</style>
        |$extraHead
        |</head>
        |<body>
        |<header class="site"><a class="name" href="/">${Html.escape(author)}</a><nav><a href="/notes/">Notes</a><a href="/">The site</a></nav></header>
        |<main>
        |$body
        |</main>
        |</body>
        |</html>
        |""".trimMargin()

    private companion object {
        /** DESIGN.md's tokens and type, for pages outside the canvas. */
        val STYLE = """
            :root{color-scheme:dark;--night:#0D1013;--slate:#161B20;--fog:#8C9499;--mist:#D6D2C8;--parchment:#EDE6D6;--lamp:#D8A15A;--hairline:rgb(237 230 214/.08)}
            *{box-sizing:border-box}
            html{background:var(--night)}
            body{margin:0;color:var(--mist);font:400 17px/1.65 Manrope,system-ui,sans-serif}
            header.site,main{max-width:46rem;margin:0 auto;padding:0 20px}
            header.site{display:flex;flex-wrap:wrap;gap:12px 24px;align-items:baseline;justify-content:space-between;padding-top:28px}
            header.site .name{font:italic 300 24px/1 "Cormorant Garamond",Georgia,serif;color:var(--parchment);text-decoration:none}
            header.site nav{display:flex;gap:20px}
            header.site nav a{font:600 11px/1 Manrope,sans-serif;letter-spacing:.3em;text-transform:uppercase;color:var(--fog);text-decoration:none}
            header.site nav a:hover{color:var(--parchment)}
            main{padding-top:48px;padding-bottom:96px}
            h1{font:italic 300 clamp(40px,8vw,64px)/1 "Cormorant Garamond",Georgia,serif;color:var(--parchment);margin:8px 0 32px}
            h2{font:italic 400 32px/1.15 "Cormorant Garamond",Georgia,serif;color:var(--parchment);margin:56px 0 12px}
            h3{font:600 12px/1.4 Manrope,sans-serif;letter-spacing:.24em;text-transform:uppercase;color:var(--parchment);margin:40px 0 8px}
            .meta{font:400 13px/1.5 "JetBrains Mono",ui-monospace,monospace;letter-spacing:.04em;color:var(--fog);margin:0}
            a{color:var(--lamp);text-decoration-thickness:1px;text-underline-offset:3px}
            strong{color:var(--parchment);font-weight:600}
            code{font:400 .86em/1.5 "JetBrains Mono",ui-monospace,monospace;background:var(--slate);padding:.1em .35em;border-radius:2px}
            pre{background:var(--slate);border:1px solid var(--hairline);border-radius:2px;padding:16px 18px;overflow-x:auto}
            pre code{background:none;padding:0;font-size:13.5px}
            blockquote{margin:28px 0;padding-left:20px;border-left:1px solid var(--lamp);font-style:italic;color:var(--parchment)}
            hr{border:0;border-top:1px solid var(--hairline);margin:48px 0}
            table{display:block;overflow-x:auto;border-collapse:collapse;font-size:15px;margin:24px 0}
            th,td{text-align:left;padding:8px 14px 8px 0;border-bottom:1px solid var(--hairline)}
            th{font-weight:600;color:var(--parchment)}
            li{margin:6px 0}
            .notes{list-style:none;padding:0}
            .notes li{margin:0 0 40px}
            .notes .title{font:italic 300 32px/1.1 "Cormorant Garamond",Georgia,serif;color:var(--parchment);text-decoration:none}
            .notes .title:hover{color:var(--lamp)}
            .notes p{margin:6px 0 0}
            .after{margin-top:64px;padding-top:20px;border-top:1px solid var(--hairline);color:var(--fog)}
            :focus-visible{outline:1px solid var(--lamp);outline-offset:3px}
        """.trimIndent().replace("\n", "")
    }
}

/** A JSON object from [fields]: strings, nested maps of strings, nothing else. */
internal fun json(vararg fields: Pair<String, Any>): String = json(fields.toMap())

private fun json(fields: Map<String, Any>): String = fields.entries.joinToString(",", "{", "}") { (key, value) ->
    @Suppress("UNCHECKED_CAST")
    val encoded = when (value) {
        is Map<*, *> -> json(value as Map<String, Any>)
        else -> jsonString(value.toString())
    }
    "${jsonString(key)}:$encoded"
}

private fun jsonString(text: String): String = buildString {
    append('"')
    for (c in text) {
        when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            // Inside <script>, "</" would end the element.
            c == '<' -> append("\\u003c")
            c < ' ' -> append("\\u" + c.code.toString(16).padStart(4, '0'))
            else -> append(c)
        }
    }
    append('"')
}
