package com.cdodi.features.life

import com.cdodi.core.navigation.graph.Destination
import com.cdodi.core.navigation.graph.Route
import com.cdodi.data.gameoflife.LifeRule

/** The Game of Life, playing [rule] in B/S notation (`B36/S23` is HighLife). In the URL: `life?rule=B36/S23`. */
data class LifeDestination(val rule: LifeRule = LifeRule.Conway) : Destination {
    override val route get() = LifeDestination

    companion object : Route<LifeDestination>("life") {
        override fun encode(destination: LifeDestination) = mapOf("rule" to destination.rule.toString())

        override fun decode(arguments: Map<String, String>): LifeDestination? {
            val rule = arguments["rule"] ?: return LifeDestination()
            return try {
                LifeDestination(LifeRule.parse(rule))
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
