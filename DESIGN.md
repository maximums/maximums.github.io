# Design — "Autumn Rain"

_Visual design of the site, written 2026-09-22. The live mockups are on the design canvas: <https://claude.ai/artifact/MjvQtfDauuPdM7iYgvWELt> (private; share it from its Share menu). This file is the source of truth for the tokens and rules; the canvas shows them in use. How the pieces are built is in `ARCHITECTURE.md`, and when in `PLAN.md`._

## 1. Brief

- **Minimal, but rich.** Few elements on screen, and each one is rich: animation, shape, detail. Not a flat, sparse minimalism.
- **The UI belongs to the weather.** Elements complement each other and the background shader. They stay clearly visible, but look like they're part of the scene: a pane of wet glass over a rainy street at night.
- **Theme:** autumn — its colours, its rain, its mood.
- **Keywords:** rain, fog, mysteries, poetry.
- **Mood:** melancholy, even a touch of depression. Quiet and heavy, never cheerful or busy.

## 2. Principles

1. **Glass, not cards.** Every surface is a pane over the scene: fogged by default, cleared when you engage with it. The scene always shows through.
2. **Light gathers, nothing jumps.** Hover and focus *warm* an element (lamp light rises, fog clears, a thread of amber grows) instead of moving or scaling it.
3. **Heavy, not fast.** Motion takes 700–1400 ms and settles like water. Nothing bounces or snaps.
4. **The weather decides.** The scene never stops and never hurries. Transitions wait for it; a page arrives when its scene is ready.
5. **One accent.** Lamp amber is the only colour that means "this matters". Everything else is night, fog and paper.
6. **Words, not chrome.** Navigation and choices are set in the italic serif; there are no tabs, icons-for-labels or badges.

## 3. Colour

| Token | Hex | Role |
|---|---|---|
| `Night` | `#0D1013` | Ground. Never pure black. |
| `Slate` | `#161B20` | Raised ground, top bar. |
| `WetStone` | `#2E363D` | Slider tracks, dividers, idle strokes. |
| `Fog` | `#8C9499` | Secondary text, labels (6.2:1 on Night). |
| `Mist` | `#D6D2C8` | Body text. |
| `Parchment` | `#EDE6D6` | Headings, active items, slider thumbs. |
| `Lamp` | `#D8A15A` | **The accent**: light, focus, the active thread (8.3:1 on Night). |
| `Ember` | `#B4532E` | Heat and age: tail lights, old Life cells, warnings. **Large text or non-text only** (3.8:1). |
| `Rain` | `#7E97A3` | Cold detail: rain streaks, grid lines, passive bars. |

Derived values used across the mockups:

| Use | Value |
|---|---|
| Hairlines | `Parchment` at 6–10 % alpha |
| Fog pane fill | `rgb(16 20 24 / 0.58)` |
| Cleared pane fill | `rgb(16 20 24 / 0.16)` |
| Lamp glow | `0 0 12px 1px Lamp @ 60–70 %` (threads), `0 0 24px -8px Lamp @ 60 %` (buttons) |
| Scene fog | `rgb(140 148 152)` at 36–78 %, heavily blurred |

In Compose these become `ui/theme/AutumnColors` with exactly these names, so a token reads the same in the design and in the code.

## 4. Type

| Role | Face | Style | Size / line | Tracking |
|---|---|---|---|---|
| Display | Cormorant Garamond | Light Italic | 88–132 / 0.9–1.0 | 0 |
| Title | Cormorant Garamond | Light Italic | 40 / 44 | 0 |
| Navigation, choices | Cormorant Garamond | Light Italic | 19–23 | 0 |
| Body | Manrope | Regular | 16 / 26 | 0 |
| Label | Manrope | SemiBold, UPPERCASE | 10–11 | 0.30 em |
| Readout | JetBrains Mono | Light / Regular | 11–16 / 1.5 | 0.04–0.08 em |

- A poet's serif for everything expressive, a quiet sans for reading, an instrument's mono for numbers.
- All three are open-source (SIL OFL). **They must be bundled as compose resources**: Compose draws text on its own canvas, so CSS web fonts don't reach it.

## 5. Surfaces

| Surface | Spec | Used for |
|---|---|---|
| **Fog pane** (default) | Fog pane fill, blur 18, saturation 0.8, 1 px hairline, inner top highlight, fine condensation texture, radius 2 | Every control panel |
| **Cleared** (hover / focus / active) | Cleared pane fill, blur 2; transitions over 1.3 s | Engaged panes, the Home window |
| **Outline** | No fill, 1 px hairline at 10–12 % | HUDs and passive readouts that must not hide the scene |

**Implementation:** Compose can't blur the WebGPU canvas underneath it (they're separate canvases). So the fog on a pane is drawn by the **GPU scene**: Compose reports each pane's shape and position, and the scene blurs and fogs those regions, adding condensation and drops. Compose draws only the text, outlines and controls on top. See `PLAN.md` Phase 4, "GPU-backed widgets".

## 6. Components

| Component | Look | States |
|---|---|---|
| **Lamp button** | Pill, 48 px tall, 1 px Lamp @ 50 % border, Lamp @ 8 % fill, soft lamp glow, Manrope 13/500 | Hover: fill 16 %, stronger glow |
| **Quiet button** | Pill, 48 px, hairline border, Mist text | Hover: border 35 %, Parchment text |
| **Round button** | 48 px circle, lamp border and glow, stroke icon (play, pause) | Hover: stronger glow |
| **Navigation** | Serif italic words, 44 px apart | Idle Fog · hover Parchment + faint glow · active Parchment + a 1 px **lamp thread** underneath |
| **Slider** | A 1 px thread (Lamp up to the value, WetStone after) and a **falling-drop thumb** (Parchment teardrop, lamp glow) | Focus: lamp ring + stronger glow |
| **Choice (segmented)** | Serif italic options over one hairline | Selected: Parchment + lamp thread |
| **Toggle** | 40×20 outline pill with a 12 px knob | On: lamp border, Parchment knob with glow |
| **Readout** | Mono rows: name · a 1 px bar with glow · value; Lamp for the dominant value, Rain for the rest | — |

- Touch targets are at least 44 px, focus is always visible (1 px Lamp, 3 px offset), and every control is a real button, link or input.
- Icons are thin stroke SVGs (1.4 px), never emoji or filled glyphs.

## 7. Motion

| Token | Value |
|---|---|
| `Settle` easing | `cubic-bezier(.22, .61, .36, 1)` |
| Hover, focus, state changes | 900–1300 ms, `Settle` |
| Transition segments | 900–1400 ms each (see §9) |
| Idle life | Bokeh breathe (7 s), fog drift (40 s), rain falls continuously; embers flicker gently (2.4 s) |

- Every animation takes its time from the **heartbeat** (`ARCHITECTURE.md` §5.2), so the fog on the glass, the scene and the transitions move as one.
- **Reduced motion:** the rain and fog freeze on a still frame, and transitions become a plain 400 ms cross-fade. Hover warmth stays, since it doesn't move anything.

## 8. Screens

| Screen | Composition |
|---|---|
| **Home** | Your triangles-around-a-rhombus menu, reimagined as a **fogged window of four panes** with corner marks around it. The rhombus holds "Welcome", a lamp thread and the name. Hovering a pane wipes its fog off, the label catches the lamp and its thread grows; drops slide down the glass. Panes: About, Game of Life, Boids, Notes. |
| **Game of Life** | Cells are **embers**: fresh cells glow amber and cool to rust as they age; recently dead cells leave a faint ghost. A faint Rain-coloured grid. One fog pane (bottom left): title, rule code, rule choice, play/step/clear, speed, and generation/alive/grid readouts. |
| **Boids** | A **murmuration** of dark birds against a lit fog bank, a few catching the lamp light. A fog pane on the right with the rule sliders and toggles, an outline readout of GPU time per pass on the left. |
| **No WebGPU** | Static, dimmed scene; one pane: "The rain can't reach this browser.", which browsers work, and a way to continue (the CPU Game of Life). |

Shared: a slim top bar (brand mark and name on the left, serif navigation in the centre), and hints in spaced-out mono in the bottom corners.

## 9. Transition — "Breath on the window"

The signature transition from Home to a scene page. Built from four transitions with `+` (`PLAN.md` Phase 2.3):

| # | Segment | Completes | UI (Compose) | GPU (WebGPU) |
|---|---|---|---|---|
| 1 | `wipe` | after 900 ms | `label-glow` | `fog-wipe` on the chosen pane, `fog-thicken` on the others |
| 2 | `fogRoll` | after 1100 ms | `menu-dissolve` | `fog-roll` in from the corners |
| 3 | `holdFog` | at least 600 ms **and** scene ready (timeout 3 s) | a caption line fades in | `fog-breathe` loops; the target scene prepares |
| 4 | `clearing` | after 1400 ms | `condense` (starts 500 ms in) | `fog-clear`, parting from the centre |

```kotlin
val breathOnTheWindow = wipe + fogRoll + holdFog + clearing
edge(from = Home, to = anyOf(Life, Boids), transition = breathOnTheWindow, onInterrupt = Interrupt.Reverse)
```

- **Going back** reverses it: the page evaporates off the glass, fog returns, the window re-forms, the pane fogs over.
- **Waiting is part of the mood:** a quick scene still gets the 600 ms of fog, a slow one gets breathing fog, and after 3 s the fog clears regardless.
- The effect ids in the table are the ones the UI and GPU effect registries register (`ARCHITECTURE.md` §4.6).
- The timings are a first guess, to be tuned in the prototype on the canvas.

## 10. Open items

- [ ] Phone layouts (the four panes and the fog panes need a different arrangement).
- [ ] Final copy. Placeholders today: "stay a while — it's raining", "a murmuration under the rain", "the flock is gathering". The readout numbers on the canvas are sample values.
- [ ] Confirm the fourth Home pane, "Notes" (for the write-ups from `PLAN.md` Phase 7).
- [ ] Tune the transition timings.
- [ ] Contrast: the mockups use `#6F777C` for the small colophon line and past segment rows. That is 4.2:1, just under 4.5:1 for 11 px text; use `Fog` instead.
- [ ] The old cyan (`#5ad6ff`) is retired; remove it from the code when the theme lands.
