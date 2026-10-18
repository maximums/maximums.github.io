package com.cdodi.features.notes

import blog.composeapp.generated.resources.Res
import com.cdodi.notes.Note

/**
 * The notes in `composeResources/files/notes`, in the order `index.txt` lists them: read once, then kept. The build
 * writes the same files as plain HTML pages (`generateNotesSite`).
 */
object NotesLibrary {
    private var notes: List<Note>? = null

    suspend fun all(): List<Note> = notes ?: Note.index(read("index.txt")).map { Note.parse(it, read("$it.md")) }.also { notes = it }

    private suspend fun read(name: String) = Res.readBytes("files/notes/$name").decodeToString()
}
