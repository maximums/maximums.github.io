package com.cdodi.core.navigation

import com.cdodi.core.navigation.graph.Destination
import com.cdodi.core.navigation.graph.NavGraph

/**
 * Destinations to and from URL strings, so every page can be linked: `life?rule=B36/S23`. The path picks the route
 * and the query holds its arguments. Where the string goes in the URL (hash or path) is up to the browser adapter.
 */
class DestinationCodec(private val graph: NavGraph) {

    fun encode(destination: Destination): String {
        val route = destination.route
        val arguments = route.encodeUnchecked(destination)
        if (arguments.isEmpty()) return route.path
        return arguments.entries.joinToString("&", prefix = "${route.path}?") { (key, value) -> "${escape(key)}=${escape(value)}" }
    }

    /** The destination [url] names, or null if no route has its path, it is malformed, or its arguments are invalid. */
    fun decode(url: String): Destination? {
        val path = url.substringBefore('?').trim('/')
        val query = url.substringAfter('?', missingDelimiterValue = "")
        val route = graph.route(path) ?: return null
        val arguments = query.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val key = unescape(pair.substringBefore('=')) ?: return null
            val value = unescape(pair.substringAfter('=', missingDelimiterValue = "")) ?: return null
            key to value
        }
        return route.decode(arguments)
    }
}

private const val HEX = "0123456789ABCDEF"

/** RFC 3986 unreserved characters, plus the ones a query allows that people like to read (`rule=B36/S23`). */
private fun Char.staysAsIs(): Boolean = this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "-._~/:@,"

/** Percent-encodes the UTF-8 bytes of [text], except the characters that can stay. */
private fun escape(text: String): String = buildString {
    for (byte in text.encodeToByteArray()) {
        val value = byte.toInt() and 0xFF
        if (value < 0x80 && value.toChar().staysAsIs()) append(value.toChar())
        else append('%').append(HEX[value shr 4]).append(HEX[value and 0xF])
    }
}

/** The text [escape] made, or null if it is not valid percent-encoded UTF-8. */
private fun unescape(text: String): String? {
    val bytes = ArrayList<Byte>(text.length)
    var i = 0
    while (i < text.length) {
        if (text[i] == '%') {
            val high = text.getOrNull(i + 1)?.digitToIntOrNull(16) ?: return null
            val low = text.getOrNull(i + 2)?.digitToIntOrNull(16) ?: return null
            bytes += (high * 16 + low).toByte()
            i += 3
        } else {
            // Up to the next escape in one go, so a surrogate pair is never split.
            val end = text.indexOf('%', i).takeIf { it >= 0 } ?: text.length
            text.substring(i, end).encodeToByteArray().forEach { bytes += it }
            i = end
        }
    }
    return try {
        bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
    } catch (_: CharacterCodingException) {
        null
    }
}
