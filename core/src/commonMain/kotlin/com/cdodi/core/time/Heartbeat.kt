package com.cdodi.core.time

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One frame as a [Clock] sees it: shared [index] and [timeNanos], the clock's own [dt] and [elapsed] (seconds). */
data class Frame(val index: Long, val timeNanos: Long, val dt: Float, val elapsed: Double)

/**
 * The order in which a frame is dispatched. Everything in one frame sees the same [Frame.index]: input is applied,
 * simulations step, navigation transitions advance, GPU passes are encoded, then UI state is published.
 */
enum class FramePhase { Input, Simulation, Navigation, Render, Ui }

fun interface Subscription : AutoCloseable

/**
 * A source of time. The [Heartbeat] is the root; [child] clocks derive from it and can be paused or slowed on their
 * own (pausing the Life simulation stops only its clock). Scales multiply down the tree, and a paused clock also
 * stops its children.
 */
interface Clock {
    val name: String
    var timeScale: Float
    var isPaused: Boolean

    /** Seconds this clock has advanced, excluding paused time and including its scale. Never wraps. */
    val elapsed: Double

    /** The child clock called [name], created on first use; asking again returns the same clock. */
    fun child(name: String): Clock

    /** Called synchronously in [phase] of every frame, with this clock's view of the frame. */
    fun subscribe(phase: FramePhase, listener: (Frame) -> Unit): Subscription

    /**
     * [elapsed] folded into `[0, period)`, for passing time to a shader as an f32 without losing precision: after
     * hours, a raw f32 of seconds only has millisecond steps. Folding is done in Double, so it never jumps.
     */
    fun elapsedFolded(period: Double): Float = (elapsed % period).toFloat()
}

/**
 * The single source of truth for time. Something drives it with [tick] (in the site, Compose's frame clock);
 * every animation, simulation and transition takes its time from it or from one of its child clocks.
 */
interface Heartbeat : Clock {
    /** The root clock's latest frame, published in [FramePhase.Ui], for readers such as Compose. */
    val frame: StateFlow<Frame>

    fun tick(timeNanos: Long)
}

/**
 * @param maxDt longest step a single frame may take (seconds); after a stall (tab in background, debugger) time
 * resumes smoothly instead of jumping.
 */
class DefaultHeartbeat(private val maxDt: Float = 0.1f) : Heartbeat {

    private val root = ClockNode("heartbeat", parent = null)
    private val subscriptions = FramePhase.entries.associateWith { mutableListOf<Registration>() }
    private val frameState = MutableStateFlow(Frame(index = 0, timeNanos = 0, dt = 0f, elapsed = 0.0))
    private var lastTimeNanos: Long? = null
    private var index = 0L

    override val frame: StateFlow<Frame> = frameState.asStateFlow()
    override val name: String get() = root.name
    override var timeScale: Float by root::timeScale
    override var isPaused: Boolean by root::isPaused
    override val elapsed: Double get() = root.elapsed

    override fun child(name: String): Clock = root.child(name)

    override fun subscribe(phase: FramePhase, listener: (Frame) -> Unit): Subscription = root.subscribe(phase, listener)

    override fun tick(timeNanos: Long) {
        // Double all the way down: summing Float frame times drifts noticeably over hours.
        val rawDt = lastTimeNanos?.let { ((timeNanos - it) / 1e9).coerceIn(0.0, maxDt.toDouble()) } ?: 0.0
        lastTimeNanos = timeNanos
        index++
        root.advance(rawDt)

        for (phase in FramePhase.entries) {
            if (phase == FramePhase.Ui) frameState.value = root.frame(index, timeNanos)
            // A snapshot: listeners may subscribe or unsubscribe while being called.
            for (registration in subscriptions.getValue(phase).toList()) {
                if (registration.isActive) registration.listener(registration.clock.frame(index, timeNanos))
            }
        }
    }

    private class Registration(val clock: ClockNode, val listener: (Frame) -> Unit) {
        var isActive = true
    }

    private inner class ClockNode(override val name: String, private val parent: ClockNode?) : Clock {
        private val children = mutableListOf<ClockNode>()
        private var lastDt = 0.0

        override var timeScale = 1f
            set(value) {
                require(value >= 0f && value.isFinite()) { "timeScale must be finite and >= 0, got $value" }
                field = value
            }
        override var isPaused = false
        override var elapsed = 0.0
            private set

        /** Product of the scales up to the root; 0 if this clock or an ancestor is paused. */
        val effectiveScale: Float
            get() = if (isPaused) 0f else timeScale * (parent?.effectiveScale ?: 1f)

        fun advance(rawDt: Double) {
            lastDt = rawDt * effectiveScale
            elapsed += lastDt
            children.forEach { it.advance(rawDt) }
        }

        fun frame(index: Long, timeNanos: Long) = Frame(index, timeNanos, lastDt.toFloat(), elapsed)

        override fun child(name: String): Clock =
            children.firstOrNull { it.name == name } ?: ClockNode(name, parent = this).also { children += it }

        override fun subscribe(phase: FramePhase, listener: (Frame) -> Unit): Subscription {
            val registration = Registration(this, listener)
            subscriptions.getValue(phase) += registration
            return Subscription {
                registration.isActive = false
                subscriptions.getValue(phase) -= registration
            }
        }
    }
}
