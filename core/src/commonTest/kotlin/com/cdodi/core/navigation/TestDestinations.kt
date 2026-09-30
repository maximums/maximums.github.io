package com.cdodi.core.navigation

import com.cdodi.core.navigation.graph.Destination
import com.cdodi.core.navigation.graph.Route
import com.cdodi.core.navigation.graph.SingletonDestination

data object Home : SingletonDestination("home")

data object About : SingletonDestination("about")

data object Boids : SingletonDestination("boids")

data object Elsewhere : SingletonDestination("elsewhere")

data class Life(val rule: String = "B3/S23") : Destination {
    override val route get() = Life

    companion object : Route<Life>("life") {
        private val validRule = Regex("B[0-8]*/S[0-8]*")

        override fun encode(destination: Life) = mapOf("rule" to destination.rule)

        override fun decode(arguments: Map<String, String>): Life? {
            val rule = arguments["rule"] ?: return Life()
            return if (validRule.matches(rule)) Life(rule) else null
        }
    }
}

/** Takes any text as its argument, to check the URL encoding. */
data class Poem(val line: String) : Destination {
    override val route get() = Poem

    companion object : Route<Poem>("poem") {
        override fun encode(destination: Poem) = mapOf("line" to destination.line)

        override fun decode(arguments: Map<String, String>) = arguments["line"]?.let(::Poem)
    }
}
