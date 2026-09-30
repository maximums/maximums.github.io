package com.cdodi.core.navigation.graph

import com.cdodi.core.navigation.signal.Signal

/**
 * A place in the site, possibly with arguments (`LifeDestination(rule = "B36/S23")`). Destinations are compared with
 * `equals`, so make them data classes or data objects. Which edge applies depends only on the [route], never on the
 * arguments.
 */
interface Destination {
    val route: Route<*>
}

/**
 * A kind of destination: its URL path and how its arguments map to and from the URL query. A route is also an
 * [EdgeMatcher] that matches exactly itself, so it goes straight into edges. A destination with arguments uses its
 * companion object as its route:
 *
 * ```
 * data class LifeDestination(val rule: String = "B3/S23") : Destination {
 *     override val route get() = LifeDestination
 *
 *     companion object : Route<LifeDestination>("life") {
 *         override fun encode(destination: LifeDestination) = mapOf("rule" to destination.rule)
 *         override fun decode(arguments: Map<String, String>) = LifeDestination(arguments["rule"] ?: "B3/S23")
 *     }
 * }
 * ```
 */
abstract class Route<D : Destination>(val path: String) : EdgeMatcher {

    /** The destination's arguments, as they appear in the URL query. */
    open fun encode(destination: D): Map<String, String> = emptyMap()

    /** The destination these URL arguments describe, or null if they are invalid. */
    abstract fun decode(arguments: Map<String, String>): D?

    override val specificity: Int get() = 2

    override fun matches(route: Route<*>): Boolean = route == this

    @Suppress("UNCHECKED_CAST")
    internal fun encodeUnchecked(destination: Destination): Map<String, String> = encode(destination as D)

    override fun toString(): String = path
}

/** A destination without arguments. There is only one of it, so it is its own route: `data object Home : SingletonDestination("home")`. */
abstract class SingletonDestination(path: String) : Route<SingletonDestination>(path), Destination {
    override val route: Route<*> get() = this

    override fun decode(arguments: Map<String, String>): SingletonDestination = this
}

/** Raised by the scene host once [route]'s scene is ready to show (pipelines compiled, buffers allocated). */
fun Signal.Companion.sceneReady(route: Route<*>): Signal = Signal("scene-ready:${route.path}")
