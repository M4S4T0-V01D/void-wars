package dev.m4s4t0.voidwars.gl

object Shaders {
    /** Ships, enemies, rocks: flat-shaded facets with a material per vertex and the paint job's finish and decal. */
    const val LIT_VS = """
uniform mat4 uMvp;
uniform mat4 uModel;
attribute vec3 aPos;
attribute vec3 aNor;
attribute float aMat;
varying vec3 vN;
varying vec3 vObj;
varying vec3 vW;
varying float vMat;
void main() {
    vN = (uModel * vec4(aNor, 0.0)).xyz;
    vObj = aPos;
    vMat = aMat;
    vW = (uModel * vec4(aPos, 1.0)).xyz;
    gl_Position = uMvp * vec4(aPos, 1.0);
}
"""

    const val LIT_FS = """
precision mediump float;
uniform vec3 uC0;
uniform vec3 uC1;
uniform vec3 uC2;
uniform float uFinish;
uniform float uDecal;
uniform float uTime;
uniform float uFlash;
uniform vec3 uEye;
varying vec3 vN;
varying vec3 vObj;
varying vec3 vW;
varying float vMat;

float hash(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
vec3 hue(float h) { return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0); }

void main() {
    vec3 n = normalize(vN);
    vec3 V = normalize(uEye - vW);
    vec3 L = normalize(vec3(-0.45, 0.55, 0.8));
    float m = floor(vMat + 0.5);
    vec3 base;
    float specAmt = 0.35;
    float shin = 24.0;
    vec2 p = vObj.xy;
    if (m < 0.5) {
        base = uC0;
        float d = 0.0;
        if (uDecal > 0.5 && uDecal < 1.5) d = step(abs(p.y), 0.09);
        else if (uDecal < 2.5 && uDecal > 1.5) d = step(fract((p.x + abs(p.y) * 1.3) * 2.2), 0.3) * step(abs(p.y), 0.75);
        else if (uDecal < 3.5 && uDecal > 2.5) d = step(0.0, p.y);
        else if (uDecal < 4.5 && uDecal > 3.5) d = mod(floor(p.x * 5.0) + floor(p.y * 5.0), 2.0);
        else if (uDecal < 5.5 && uDecal > 4.5) d = step(0.55, sin(p.x * 11.0 + sin(p.y * 9.0) * 1.6 + abs(p.y) * 6.0));
        else if (uDecal > 5.5) {
            vec2 g = fract(p * 6.0);
            d = max(step(g.x, 0.08), step(g.y, 0.08)) * step(hash(floor(p * 6.0)), 0.55);
        }
        base = mix(base, uC1, d);
    } else if (m < 1.5) {
        base = uC1;
    } else if (m < 2.5) {
        // Glow: lit from within, ignores the light.
        vec3 g = uC2 * (1.15 + 0.15 * sin(uTime * 9.0 + p.x * 4.0));
        gl_FragColor = vec4(mix(g + vec3(0.15), vec3(1.0), uFlash), 1.0);
        return;
    } else if (m < 3.5) {
        base = vec3(0.13, 0.14, 0.18) + uC0 * 0.12;
        specAmt = 0.5; shin = 40.0;
    } else {
        base = vec3(0.03, 0.05, 0.09);
        specAmt = 1.2; shin = 60.0;
    }
    float f = uFinish;
    if (m < 1.5) {
        if (f < 0.5) { specAmt = 0.04; shin = 6.0; }
        else if (f < 1.5) { specAmt = 0.55; shin = 32.0; }
        else if (f < 2.5) { specAmt = 0.8; shin = 14.0; base *= 0.9 + 0.2 * sin(p.x * 60.0 + p.y * 7.0) * 0.5; }
        else if (f < 3.5) {
            // Chrome: a fake reflection of a sky gradient.
            vec3 r = reflect(-V, n);
            vec3 env = mix(vec3(0.05, 0.04, 0.1), vec3(0.9, 0.92, 1.0), smoothstep(-0.2, 0.9, r.y)) + uC2 * 0.25 * smoothstep(0.3, 1.0, r.x);
            base = mix(base, env, 0.65);
            specAmt = 1.2; shin = 80.0;
        } else if (f < 4.5) {
            base = mix(base, hue(dot(n, vec3(0.6, 0.3, 0.7)) * 1.4 + uTime * 0.08 + p.x * 0.5), 0.6);
            specAmt = 0.9; shin = 40.0;
        } else {
            // Void: dark paint with stars drifting underneath.
            base *= 0.25;
            vec2 sp = p * 14.0 + vec2(uTime * 0.6, 0.0);
            float s = step(0.93, hash(floor(sp)));
            float tw = 0.5 + 0.5 * sin(uTime * 3.0 + hash(floor(sp)) * 40.0);
            base += vec3(0.85, 0.8, 1.0) * s * tw * smoothstep(0.4, 0.1, length(fract(sp) - 0.5));
            specAmt = 0.7; shin = 50.0;
        }
    }
    float diff = max(dot(n, L), 0.0);
    vec3 col = base * (0.3 + 0.85 * diff);
    col += vec3(1.0) * specAmt * pow(max(dot(reflect(-L, n), V), 0.0), shin);
    float fres = pow(1.0 - max(dot(n, V), 0.0), 3.0);
    col += uC2 * fres * (m > 3.5 ? 0.9 : 0.45);
    if (f > 4.5 && m < 1.5) col += vec3(0.5, 0.2, 1.0) * fres * 0.6;
    gl_FragColor = vec4(mix(col, vec3(1.0), uFlash), 1.0);
}
"""

    /** Every sprite: glows, bullets, sparks, beams. See Shape for the numbers. */
    const val SPRITE_VS = """
uniform mat4 uVp;
attribute vec2 aPos;
attribute vec2 aUv;
attribute vec4 aCol;
attribute float aShape;
varying vec2 vUv;
varying vec4 vCol;
varying float vShape;
void main() {
    vUv = aUv;
    vCol = aCol;
    vShape = aShape;
    gl_Position = uVp * vec4(aPos, 0.6, 1.0);
}
"""

    const val SPRITE_FS = """
precision mediump float;
varying vec2 vUv;
varying vec4 vCol;
varying float vShape;
void main() {
    vec2 uv = vUv;
    float d = length(uv);
    vec3 c = vCol.rgb;
    float s = vShape;
    vec4 o;
    if (s < 0.5) {
        // Orb: white-hot core, coloured glow.
        float core = smoothstep(0.5, 0.15, d);
        float glow = exp(-d * d * 4.0) * (1.0 - smoothstep(0.75, 1.0, d));
        o = vec4(mix(c, vec3(1.0), core * 0.85) * (core + glow * 0.9), 0.0);
    } else if (s < 1.5) {
        float g = smoothstep(0.18, 0.0, abs(d - 0.78)) * (1.0 - smoothstep(0.97, 1.0, d));
        o = vec4(c * g * 1.2, 0.0);
    } else if (s < 2.5) {
        float g = 1.0 - smoothstep(0.0, 1.0, d);
        o = vec4((c + vec3(g * 0.6)) * g * g * 1.4, 0.0);
    } else if (s < 3.5) {
        float g = exp(-d * d * 4.0) * (1.0 - smoothstep(0.7, 1.0, d));
        o = vec4(c * g, 0.0);
    } else if (s < 4.5) {
        float dd = abs(uv.x) + abs(uv.y);
        float g = smoothstep(1.0, 0.75, dd);
        o = vec4(c * g * (0.6 + 0.4 * uv.y), g * 0.7);
    } else if (s < 5.5) {
        // Beam: a soft band with a white core, fading at its ends.
        float a = abs(uv.y);
        float ends = 1.0 - smoothstep(0.85, 1.0, abs(uv.x));
        float g = exp(-a * a * 5.0) * ends;
        float core = smoothstep(0.32, 0.0, a) * ends;
        o = vec4(c * g * 1.2 + vec3(core), 0.0);
    } else if (s < 6.5) {
        float star = max(0.0, 1.0 - abs(uv.x * uv.y) * 30.0) * (1.0 - smoothstep(0.0, 1.0, d));
        float g = exp(-d * d * 9.0);
        o = vec4((c + vec3(0.3)) * (star + g), 0.0);
    } else if (s < 7.5) {
        float g = smoothstep(0.12, 0.0, abs(d - 0.86)) * (1.0 - smoothstep(0.98, 1.0, d));
        o = vec4(c * g, 0.0);
    } else if (s < 8.5) {
        // Enemy bullet: readable on anything — a dark edge, a coloured body, a white heart, and a halo.
        float body = smoothstep(0.66, 0.58, d);
        float edge = smoothstep(0.45, 0.58, d) * body;
        float core = smoothstep(0.34, 0.18, d);
        vec3 col = mix(mix(c, vec3(1.0), core), c * 0.25, edge);
        float halo = exp(-d * d * 3.0) * (1.0 - body) * (1.0 - smoothstep(0.85, 1.0, d));
        o = vec4(col * body + c * halo * 0.7, body);
    } else if (s < 9.5) {
        // Credit: a hexagonal coin.
        vec2 q = abs(uv);
        float hd = max(q.x * 0.866 + q.y * 0.5, q.y);
        float body = smoothstep(0.62, 0.55, hd);
        float shine = smoothstep(0.2, 0.0, abs(uv.x + uv.y * 0.6 - 0.1));
        float glow = exp(-d * d * 3.0) * (1.0 - body) * (1.0 - smoothstep(0.85, 1.0, d));
        o = vec4(c * body * (0.75 + 0.35 * uv.y) + vec3(shine * body * 0.6) + c * glow * 0.6, body);
    } else if (s < 10.5) {
        // A hole: darkens what's behind (the singularity).
        float g = smoothstep(1.0, 0.35, d);
        o = vec4(c * smoothstep(0.75, 0.95, d) * g * 0.8, g);
    } else {
        o = vec4(c, 0.0);
    }
    gl_FragColor = o * vCol.a;
}
"""

    /** The backdrop: drifting nebula, three layers of stars and a planet, all procedural. */
    const val SKY_VS = """
attribute vec2 aPos;
varying vec2 vUv;
void main() {
    vUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.999, 1.0);
}
"""

    const val SKY_FS = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform vec2 uRes;
uniform float uTime;
uniform float uScroll;
uniform vec3 uNebA;
uniform vec3 uNebB;
uniform vec3 uPlanetCol;
uniform vec3 uPlanet;
uniform float uOct;
uniform float uFlash;
uniform vec3 uFlashCol;
uniform float uDim;
varying vec2 vUv;

float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x), mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.55;
    for (int i = 0; i < 4; i++) {
        if (float(i) >= uOct) break;
        v += a * noise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

void main() {
    vec2 p = (vUv - 0.5) * vec2(uRes.x / uRes.y, 1.0) * 2.0;
    vec3 col = vec3(0.012, 0.01, 0.025);
    vec2 q = p * 1.1 + vec2(uScroll * 0.012, 0.0);
    float n1 = fbm(q * 1.2 + vec2(0.0, uTime * 0.01));
    float n2 = fbm(q * 2.2 - vec2(uTime * 0.007, 3.1));
    float norm = uOct < 1.5 ? 1.8 : (uOct < 2.5 ? 1.25 : 1.05);
    n1 *= norm; n2 *= norm;
    col += uNebA * smoothstep(0.38, 1.0, n1 + 0.25 * n2) * 0.6;
    col += uNebB * smoothstep(0.5, 1.05, n2 + 0.2 * n1) * 0.45;
    for (int l = 0; l < 3; l++) {
        float fl = float(l);
        float scale = 9.0 + fl * 7.0;
        vec2 sp = p * scale + vec2(uScroll * (0.05 + fl * 0.07) * scale * 0.1, fl * 13.0);
        vec2 id = floor(sp);
        vec2 f = fract(sp) - 0.5;
        float h = h21(id + fl * 7.1);
        if (h > 0.82) {
            vec2 off = vec2(h21(id + 3.3), h21(id + 5.7)) - 0.5;
            float dd = length(f - off * 0.7);
            float b = (h - 0.82) / 0.18;
            float tw = 0.7 + 0.3 * sin(uTime * (2.0 + h * 5.0) + h * 40.0);
            vec3 sc = mix(vec3(0.75, 0.8, 1.0), vec3(1.0, 0.85, 0.75), h21(id + 9.1));
            col += sc * smoothstep(0.05 + 0.05 * b, 0.0, dd) * b * tw * (0.6 + fl * 0.3);
        }
    }
    vec2 pc = p - uPlanet.xy;
    float r = length(pc) / uPlanet.z;
    if (r < 1.35) {
        vec3 ld = normalize(vec3(-0.6, 0.5, 0.55));
        if (r < 1.0) {
            vec3 nn = vec3(pc / uPlanet.z, sqrt(1.0 - r * r));
            float l = max(dot(nn, ld), 0.0);
            float bands = noise(vec2(nn.y * 7.0 + noise(nn.xy * 3.0 + uTime * 0.01) * 1.6, 0.5));
            vec3 pcol = uPlanetCol * (0.5 + 0.5 * bands);
            vec3 lit = pcol * (0.04 + l * 1.05) + uPlanetCol * pow(1.0 - nn.z, 3.0) * 0.6 * (0.3 + l);
            col = mix(col, lit, smoothstep(1.0, 0.985, r));
        }
        float halo = pow(max(0.0, 1.0 - abs(r - 1.0) * 4.0), 2.0);
        float side = max(0.0, dot(normalize(vec3(pc, 0.0)), ld)) * 0.8 + 0.2;
        col += uPlanetCol * halo * 0.45 * side;
    }
    vec2 v = vUv - 0.5;
    col *= 1.0 - dot(v, v) * 0.9;
    col *= uDim;
    col = mix(col, uFlashCol, uFlash * 0.3);
    gl_FragColor = vec4(col, 1.0);
}
"""
}
