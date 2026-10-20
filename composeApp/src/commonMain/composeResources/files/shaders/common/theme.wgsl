// The theme's colours, for every shader that draws in them: this goes before the shader's own source (GpuTheme does
// it). ThemeUniforms.kt writes them, in this order, whenever the theme changes; each is a colour in .rgb.

struct Theme {
    ground: vec4f,            // where nothing is drawn: Life's world, the flock's night
    accent: vec4f,            // the one colour that calls: Life's young cells, the light the birds catch
    heat: vec4f,              // Life's ageing cells
    cold: vec4f,              // Life's grid
    sky: array<vec4f, 4>,     // the street's sky, from the top down
    lights: array<vec4f, 4>,  // the street's lights by kind: lamps, tail lights, head lights, windows
    streetFog: vec4f,         // the fog banks drifting over the street
    fog: vec4f,               // the fog rolling over a scene between pages, and the flock's fog bank
    fallingNear: vec4f,       // what falls past the glass (rain streaks, in autumn): near
    fallingFar: vec4f,        // and far
    shade: vec4f,             // the dark the corners fall into
    ash: vec4f,               // Life's oldest cells
    figure: vec4f,            // the flock's birds against the fog
}

@group(1) @binding(0) var<uniform> theme: Theme;
