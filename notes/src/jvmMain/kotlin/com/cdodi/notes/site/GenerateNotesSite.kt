package com.cdodi.notes.site

import com.cdodi.notes.Note
import com.cdodi.notes.NotesSite
import java.io.File

/**
 * Writes the notes as plain HTML (see [NotesSite]). Run by the `generateNotesSite` task of `:composeApp`.
 *
 * Arguments: the notes directory (with `index.txt`), the output directory, the site's URL, and the author's name.
 */
fun main(args: Array<String>) {
    require(args.size == 4) { "usage: <notes dir> <output dir> <site url> <author>" }
    val (notesDir, outputDir, siteUrl, author) = args.map(String::trim)

    val listed = Note.index(File(notesDir, "index.txt").readText())
    val unlisted = File(notesDir).listFiles { file -> file.extension == "md" }.orEmpty().map { it.nameWithoutExtension } - listed.toSet()
    require(unlisted.isEmpty()) { "Notes not in index.txt (add them, or move them out while drafting): ${unlisted.sorted().joinToString()}" }
    val notes = listed.map { slug -> Note.parse(slug, File(notesDir, "$slug.md").readText()) }

    val output = File(outputDir)
    output.deleteRecursively()
    for ((path, content) in NotesSite(siteUrl.trimEnd('/'), author).pages(notes)) {
        File(output, path).apply { parentFile.mkdirs() }.writeText(content)
    }
    println("Wrote ${notes.size} notes to $output")
}
