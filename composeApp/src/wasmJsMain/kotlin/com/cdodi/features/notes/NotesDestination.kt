package com.cdodi.features.notes

import com.cdodi.core.navigation.graph.Destination
import com.cdodi.core.navigation.graph.Route

/** The write-ups: the list, or the note named [slug]. In the URL: `notes`, and `notes?note=webidl-compiler`. */
data class NotesDestination(val slug: String? = null) : Destination {
    override val route get() = NotesDestination

    companion object : Route<NotesDestination>("notes") {
        override fun encode(destination: NotesDestination) = destination.slug?.let { mapOf("note" to it) }.orEmpty()

        override fun decode(arguments: Map<String, String>) = NotesDestination(arguments["note"]?.takeIf { it.isNotBlank() })
    }
}
