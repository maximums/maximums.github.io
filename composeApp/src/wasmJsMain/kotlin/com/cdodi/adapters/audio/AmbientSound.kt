package com.cdodi.adapters.audio

import com.cdodi.core.bus.LifecycleBus
import com.cdodi.webaudio.bindings.AudioBuffer
import com.cdodi.webaudio.bindings.AudioContext
import com.cdodi.webaudio.bindings.BiquadFilterNode
import com.cdodi.webaudio.bindings.BiquadFilterType
import com.cdodi.webaudio.bindings.GainNode
import com.cdodi.webaudio.bindings.decodeAudioDataSuspend
import com.cdodi.webaudio.bindings.lowpass
import com.cdodi.webidl.runtime.await
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.get
import org.w3c.fetch.Response
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The rain outside the window: "Light rain on street" by BonnyOrbit (CC0, <https://freesound.org/s/380651/>), light
 * rain recorded from an open window onto a city street at night. It plays while the viewer has it on and the page is
 * active (visible and focused), through the Web Audio bindings the WebIDL compiler generates from the spec.
 *
 * - **Off until asked**, and remembered in this browser. Browsers only let a page start sound from a click or a key,
 *   so a remembered "on" waits for the viewer's first one.
 * - **No seam.** The recording plays over and over, each pass starting [CROSSFADE] seconds before the last one ends,
 *   with an equal-power crossfade (rain is noise: two passes overlapping don't dip or swell). The first pass starts
 *   somewhere random, so no two visits begin alike.
 * - **Heard through the glass**: a low-pass filter, muffled behind Home's fogged window and clearer on the pages,
 *   where the glass is cleared ([setOpenness]).
 * - **Only while active.** It fades out when the page is hidden or loses focus, then the audio context is suspended
 *   (no work while away), and it fades back in on return.
 */
class AmbientSound(
    private val lifecycle: LifecycleBus,
    private val scope: CoroutineScope,
    private val url: String = "sounds/rain.mp3",
) {
    private val enabledState = MutableStateFlow(load())

    /** Whether the viewer has the rain on (whether or not it's audible right now). */
    val enabled: StateFlow<Boolean> = enabledState.asStateFlow()

    private var context: AudioContext? = null
    private var filter: BiquadFilterNode? = null
    private var master: GainNode? = null
    private var buffer: AudioBuffer? = null
    private var level = BASE_LEVEL
    private var openness = 0f
    private var nextPass = -1.0
    private var scheduler: Job? = null
    private var suspender: Job? = null
    private var waitingForGesture = false

    private val fadeIn = equalPowerCurve(rising = true)
    private val fadeOut = equalPowerCurve(rising = false)

    init {
        scope.launch { lifecycle.state.map { it.isActive }.distinctUntilChanged().collect { update() } }
    }

    /** Turns the rain on or off. Called from a click or a key, which is what lets the browser start it. */
    fun setEnabled(on: Boolean) {
        enabledState.value = on
        save(on)
        update()
    }

    /** How open the glass is: 0 behind Home's fogged window, 1 cleared. The sound follows smoothly. */
    fun setOpenness(value: Float) {
        val target = value.coerceIn(0f, 1f)
        if (kotlin.math.abs(target - openness) < 0.01f) return
        openness = target
        val context = context ?: return
        filter?.frequency?.setTargetAtTime(cutoff(openness), context.currentTime, 0.6f)
    }

    private fun update() {
        if (enabledState.value && lifecycle.state.value.isActive) play() else quieten()
    }

    private fun play() {
        val context = context ?: if (hasUserActivation()) build() else return waitForGesture()
        suspender?.cancel()
        context.resume()
        val master = master ?: return
        master.gain.cancelScheduledValues(context.currentTime)
        master.gain.setTargetAtTime(level, context.currentTime, 0.8f)
        if (scheduler?.isActive != true) {
            scheduler = scope.launch {
                while (isActive) {
                    schedulePasses()
                    delay(2_000)
                }
            }
        }
    }

    private fun quieten() {
        val context = context ?: return
        master?.gain?.let {
            it.cancelScheduledValues(context.currentTime)
            it.setTargetAtTime(0f, context.currentTime, 0.35f)
        }
        scheduler?.cancel()
        suspender?.cancel()
        // Once it's silent, the context sleeps: nothing is computed while the page is away.
        suspender = scope.launch {
            delay(1_800)
            context.suspend()
        }
    }

    /** The audio graph: every pass → the glass (a low-pass filter) → the master level → the speakers. */
    private fun build(): AudioContext {
        val context = AudioContext()
        val filter = context.createBiquadFilter().apply {
            type = BiquadFilterType.lowpass
            Q.value = 0.7f
            frequency.value = cutoff(openness)
        }
        val master = context.createGain().apply { gain.value = 0f }
        filter.connect(master)
        master.connect(context.destination)
        this.context = context
        this.filter = filter
        this.master = master
        scope.launch {
            try {
                val bytes = window.fetch(url).await<Response>().arrayBuffer().await<ArrayBuffer>()
                val decoded = context.decodeAudioDataSuspend(bytes)
                level = BASE_LEVEL * loudnessCorrection(decoded)
                buffer = decoded
                update()
            } catch (e: Throwable) {
                println("The rain couldn't load: ${e.message}")
            }
        }
        return context
    }

    /** Keeps passes queued [LOOKAHEAD] seconds ahead of the context's clock, which stands still while suspended. */
    private fun schedulePasses() {
        val context = context ?: return
        val buffer = buffer ?: return
        val now = context.currentTime
        if (nextPass < 0) {
            // The first pass starts somewhere random in the recording and fades in like any other.
            val offset = Random.nextDouble(0.0, buffer.duration - CROSSFADE * 3)
            pass(context, buffer, now + 0.05, offset)
        }
        while (nextPass < now + LOOKAHEAD) pass(context, buffer, maxOf(nextPass, now + 0.05), 0.0)
    }

    /** One pass of the recording from [offset], starting at [at], crossfading in and out over [CROSSFADE]. */
    private fun pass(context: AudioContext, buffer: AudioBuffer, at: Double, offset: Double) {
        val length = buffer.duration - offset
        val gain = context.createGain()
        gain.gain.setValueCurveAtTime(fadeIn, at, CROSSFADE)
        gain.gain.setValueCurveAtTime(fadeOut, at + length - CROSSFADE, CROSSFADE)
        gain.connect(filter!!)
        context.createBufferSource().apply {
            this.buffer = buffer
            connect(gain)
            start(at, offset)
            stop(at + length)
        }
        nextPass = at + length - CROSSFADE
    }

    /** Waits for the viewer's first click or key, then tries again: the only moment a browser lets sound start. */
    private fun waitForGesture() {
        if (waitingForGesture) return
        waitingForGesture = true
        val onGesture: (org.w3c.dom.events.Event) -> Unit = {
            waitingForGesture = false
            update()
        }
        window.addEventListener("pointerdown", onGesture, onceOptions())
        window.addEventListener("keydown", onGesture, onceOptions())
    }

    private fun load(): Boolean = try {
        window.localStorage.getItem(KEY) == "on"
    } catch (_: Throwable) {
        false
    }

    private fun save(on: Boolean) {
        try {
            window.localStorage.setItem(KEY, if (on) "on" else "off")
        } catch (_: Throwable) {
            // Storage blocked: the choice lasts until the page closes.
        }
    }

    private companion object {
        const val KEY = "rain-sound"

        /** Seconds two passes overlap. */
        const val CROSSFADE = 8.0

        /** Seconds of passes queued ahead. */
        const val LOOKAHEAD = 12.0

        /** The master level for a recording at [TARGET_RMS]: a background, under anything else playing. */
        const val BASE_LEVEL = 0.6f

        /** The loudness the recording is brought to (about −28 dBFS RMS). */
        const val TARGET_RMS = 0.04

        /** The low-pass cut-off: 2.4 kHz through fogged glass, 9 kHz through cleared, evenly in pitch between. */
        fun cutoff(openness: Float): Float = 2_400f * (9_000f / 2_400f).pow(openness)

        /** A gain that brings [buffer] to [TARGET_RMS], measured on every 32nd sample of its first channel. */
        fun loudnessCorrection(buffer: AudioBuffer): Float {
            val samples = buffer.getChannelData(0)
            var sum = 0.0
            var count = 0
            var i = 0
            while (i < samples.length) {
                val sample = samples[i].toDouble()
                sum += sample * sample
                count++
                i += 32
            }
            val rms = sqrt(sum / count.coerceAtLeast(1))
            return if (rms > 0) (TARGET_RMS / rms).toFloat().coerceIn(0.1f, 2f) else 1f
        }

        /** sin/cos quarter waves: the gains of two uncorrelated sounds crossfaded at equal power. */
        fun equalPowerCurve(rising: Boolean): JsArray<JsNumber> = List(64) { i ->
            val t = i / 63.0 * PI / 2
            (if (rising) sin(t) else cos(t)).toJsNumber()
        }.toJsArray()
    }
}

/** Whether a click or key is being handled right now (`navigator.userActivation`; true where it's unknown). */
private fun hasUserActivation(): Boolean = js("navigator.userActivation ? navigator.userActivation.isActive : true")

private fun onceOptions(): org.w3c.dom.AddEventListenerOptions = js("({ once: true, passive: true })")
