---
title: A navigation graph where the edges are the transitions
date: 2026-10-18
summary: Pages are nodes, and every edge carries its transition as a value. A transition can be timed or wait for a signal, and transitions combine with +, with and or. Compose and WebGPU play them from one clock.
---

Click a pane on this site and several things happen in order. The menu folds into the top bar. Fog rolls in over the glass. The next scene compiles behind the fog while it breathes. Then the fog parts and the page fades in. Half of that is Compose and half is WebGPU, on two different canvases, and one step waits for the GPU to finish preparing a scene. Navigation libraries attach a transition to a screen or a host. I wanted transitions to belong to the *route between two pages* instead.

## The model knows nothing about the screen

The navigation model lives in a module that is pure Kotlin: no Compose, no WebGPU, no DOM. Destinations are the nodes:

```kotlin
data object AboutDestination : SingletonDestination("about")

data class LifeDestination(val rule: LifeRule = LifeRule.Conway) : Destination
```

Each destination's route encodes it in the URL, so every page is a link: `#/life?rule=B36/S23` opens the Game of Life playing HighLife.

The edges carry the transitions:

```kotlin
fun appGraph(): NavGraph = navGraph(start = HomeDestination) {
    menuOrder.forEach { destination(it) }

    edge(from = any, to = any, transition = Transitions.pageToPage, onInterrupt = Interrupt.Replace)
    edge(from = HomeDestination, to = any, transition = Transitions.openPage, onInterrupt = Interrupt.Replace).andBack()

    reducedMotion = Transitions.reducedMotion
}
```

When several edges match, the most specific one wins: `Home → any` beats `any → any`. Two edges where neither is more specific (`Home → any` and `any → Boids`, going from Home to Boids) are an error, reported when the graph is built rather than halfway through a click. So are unreachable destinations, duplicate paths, and effects nothing can draw. `andBack()` adds the opposite edge with the transition played in reverse.

## Transitions are values

A basic transition is a completion rule plus the effects it plays:

```kotlin
val melt = transition(after(1.seconds)) { play(AnimatedEffect(UiEffectIds.PIXEL_MELT)) }

val holdFog = transition(atLeast(600.milliseconds) and whenever(Signal.targetSceneReady, timeout = 3.seconds)) {
    play(ShaderEffect(GpuEffectIds.FOG_HOLD))
}
```

A completion can be time (`after`, with an easing), a minimum time (`atLeast`), a signal (`whenever`, with an optional timeout), or a combination of those with `and` and `or`. Transitions combine with three operators, and the result is again a transition:

- `a + b`: in sequence;
- `a with b`: in parallel, done when both are;
- `a or b`: a race, done when the first one is.

So the site's two transitions read like a description of what you see:

```kotlin
val pageToPage = (melt with fogRoll) + holdFog + (fade with clearing)
val openPage = (menuMorph with fogRoll) + holdFog + (fade with clearing)
```

The operators flatten as they build, so `(a + b) + c` and `a + (b + c)` are the same value. `reversed()` plays a transition backwards: sequences in reverse order, every effect from 1 back to 0. Because transitions are plain data, tests can compare them, and the graph can list every effect they might play.

## Effects are names, not code

The model only knows effects by id: `pixel-melt`, `fade`, `menu-morph`, `fog-roll`. Each layer of the app registers renderers for the ids it can draw:

- the Compose layer fades pages, morphs the menu, and melts the page being left with a shader over its pixels;
- the WebGPU layer draws the fog on the glass.

At startup the graph checks that every effect on every edge has a renderer, so a misspelt id fails immediately.

While a transition runs, the model publishes its state every frame: where it's going from and to, and how far each running effect has travelled. The Compose layer reads that while laying out and drawing, without recomposing. The GPU layer reads it while recording its commands. Neither layer decides anything about navigation; they just draw the state they're given.

## Waiting on the GPU

`holdFog` is the step that waits. Entering the Boids page means compiling its shaders and allocating its buffers. The scene raises a `scene-ready` signal when it's done, and the fog keeps breathing until then. It lasts at least 600 ms, so a fast scene doesn't make the fog flicker, and at most 3 seconds, so a slow one doesn't hold you up. A timer and a GPU job compose the same way, because both are just completions.

## Interrupts

What happens when you click somewhere else halfway through a transition is configured on the edge too:

- `Queue` lets the transition finish, then goes on;
- `Replace` stops it as if it had finished, and goes on from there;
- `Reverse` plays what already ran backwards from the current point.

The site uses `Replace` everywhere, so a click never waits.

## One clock

Every animation takes its time from a single heartbeat, which runs in phases each frame: input, simulation, navigation, render, UI. Transitions run on a child clock of it, so they and the fog the GPU draws for them can't drift apart.

With reduced motion turned on, the graph swaps every edge's transition for a 400 ms cross-fade. That's one line, since a transition is just a value.

## The browser's back button

The model keeps a back stack of what was asked for, not of what's on screen. A small sync layer maps each browser history entry to a back-stack index. Pressing back in the browser becomes a Back intent, which plays the reversed edge like any other navigation. Typing a URL works too.

## Tests

The model's tests run on the JVM in milliseconds:

- graph validation, including ambiguous edges;
- how transitions run: sequences, races, repeats and timeouts;
- what each kind of interrupt does;
- the history sync, against a fake browser history.
