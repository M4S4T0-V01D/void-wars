package dev.m4s4t0.voidwars.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Every sound in the game. Several have a few variants, picked at random, so repeats never sound mechanical. */
enum class Snd(val variants: Int = 1) {
    SHOT(3), SHOT_HEAVY(2), LASER, RAIL, FLAK(2), MISSILE(2), ZAP(3),
    HIT(3), ROCK(3), BOOM(3), BIG_BOOM(2), BOSS_BOOM, HURT, SHIELD, GRAZE(3),
    PICKUP, COIN(2), POWER, SPECIAL, CHARGED, WARNING, ENEMY_SHOT(2), WAVE,
    TAP, BUY, LEVEL, BELL, DONE, DENY, WHOOSH,
}

/**
 * Synthesizes the sounds (mono, 48 kHz) from a handful of tiny DSP pieces: pitch-swept oscillators for the guns,
 * filtered noise for the explosions, FM bells for the interface (the same bell as the other VOID apps).
 */
object SoundBank {
    const val SR = 48000

    fun build(): Map<Snd, Array<FloatArray>> {
        val rnd = Random(11)
        return Snd.entries.associateWith { s -> Array(s.variants) { v -> normalize(make(s, v, Random(rnd.nextInt() + v * 7919)), peak(s)) } }
    }

    private fun peak(s: Snd) = when (s) {
        Snd.SHOT, Snd.ZAP, Snd.ENEMY_SHOT -> 0.45f
        Snd.HIT, Snd.GRAZE, Snd.TAP -> 0.4f
        Snd.LASER, Snd.FLAK -> 0.55f
        Snd.BELL -> 0.6f
        else -> 0.85f
    }

    private fun make(s: Snd, v: Int, r: Random): FloatArray = when (s) {
        Snd.SHOT -> pew(r, 1500.0 + v * 180, 520.0, 0.07)
        Snd.SHOT_HEAVY -> heavy(r, v)
        Snd.LASER -> laser(r)
        Snd.RAIL -> rail(r)
        Snd.FLAK -> flak(r)
        Snd.MISSILE -> missile(r)
        Snd.ZAP -> zap(r)
        Snd.HIT -> hit(r, v)
        Snd.ROCK -> rock(r)
        Snd.BOOM -> boom(r, 0.45, 1f + v * 0.12f)
        Snd.BIG_BOOM -> boom(r, 1.1, 0.75f + v * 0.1f)
        Snd.BOSS_BOOM -> bossBoom(r)
        Snd.HURT -> hurt(r)
        Snd.SHIELD -> shield()
        Snd.GRAZE -> graze(r, v)
        Snd.PICKUP -> chord(listOf(880.0 to 0.0, 1318.5 to 0.04, 1760.0 to 0.08), 0.35, 0.15)
        Snd.COIN -> chord(listOf(1567.98 + v * 200 to 0.0, 2093.0 + v * 260 to 0.05), 0.3, 0.12)
        Snd.POWER -> power()
        Snd.SPECIAL -> special(r)
        Snd.CHARGED -> chord(listOf(1046.5 to 0.0, 1568.0 to 0.06, 2093.0 to 0.12), 0.6, 0.25)
        Snd.WARNING -> warning()
        Snd.ENEMY_SHOT -> pew(r, 760.0 + v * 140, 300.0, 0.06)
        Snd.WAVE -> whoosh(r, 0.6)
        Snd.TAP -> tap()
        Snd.BUY -> chord(listOf(1318.5 to 0.0, 1975.5 to 0.05, 2637.0 to 0.1), 0.6, 0.25)
        Snd.LEVEL -> chord(listOf(523.25 to 0.0, 659.25 to 0.07, 783.99 to 0.14, 1046.5 to 0.21, 1318.5 to 0.28), 1.2, 0.4)
        Snd.BELL -> bellNote(1046.5, 1.4, 0.7, 0.6)
        Snd.DONE -> fanfare()
        Snd.DENY -> deny()
        Snd.WHOOSH -> whoosh(r, 0.35)
    }

    // ---- building blocks ----

    private fun n(sec: Double) = (sec * SR).toInt()

    private fun normalize(x: FloatArray, peak: Float): FloatArray {
        var m = 1e-6f
        for (s in x) m = max(m, abs(s))
        val k = peak / m
        // Soft clip, then a short fade so nothing ends in a click.
        for (i in x.indices) x[i] = kotlin.math.tanh(x[i] * k * 1.1f) / kotlin.math.tanh(1.1f)
        val f = min(x.size, n(0.006))
        for (i in 0 until f) x[x.size - 1 - i] *= i / f.toFloat()
        return x
    }

    /** A resonant band-pass (RBJ biquad) whose centre can glide; fed sample by sample. */
    private class Band(var q: Float) {
        private var b0 = 0f; private var b2 = 0f; private var a1 = 0f; private var a2 = 0f
        private var x1 = 0f; private var x2 = 0f; private var y1 = 0f; private var y2 = 0f
        fun tune(freq: Float) {
            val w = 2.0 * PI * freq.coerceIn(20f, SR / 2.2f) / SR
            val alpha = sin(w) / (2.0 * q)
            val a0 = 1.0 + alpha
            b0 = (alpha / a0).toFloat(); b2 = (-alpha / a0).toFloat()
            a1 = (-2.0 * cos(w) / a0).toFloat(); a2 = ((1.0 - alpha) / a0).toFloat()
        }
        fun run(x: Float): Float {
            val y = b0 * x + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }

    /** A one-pole low-pass whose corner can move. */
    private class Low(freq: Float) {
        private var a = 0f
        private var y = 0f
        init { tune(freq) }
        fun tune(freq: Float) { a = exp(-2.0 * PI * freq / SR).toFloat() }
        fun run(x: Float): Float { y = x + a * (y - x); return y }
    }

    private fun env(i: Int, attack: Double, tau: Double): Float {
        val t = i.toDouble() / SR
        val a = min(1.0, t / max(attack, 1e-4))
        return (a * exp(-max(0.0, t - attack) / tau)).toFloat()
    }

    private fun bellNote(freq: Double, sec: Double, decay: Double, bright: Double): FloatArray {
        val out = FloatArray(n(sec))
        for (i in out.indices) {
            val t = i.toDouble() / SR
            val mod = bright * 2.2 * exp(-t / (decay * 0.35)) * sin(2 * PI * freq * 3.5 * t)
            var y = sin(2 * PI * freq * t + mod)
            y += 0.35 * sin(2 * PI * freq * 2.76 * t) * exp(-t / (decay * 0.4))
            y += 0.18 * sin(2 * PI * freq * 5.4 * t) * exp(-t / (decay * 0.2))
            out[i] = (y * env(i, 0.004, decay)).toFloat()
        }
        return out
    }

    private fun mixIn(out: FloatArray, x: FloatArray, at: Int, gain: Float) {
        for (i in x.indices) if (at + i < out.size) out[at + i] += x[i] * gain
    }

    private fun chord(notes: List<Pair<Double, Double>>, sec: Double, decay: Double): FloatArray {
        val out = FloatArray(n(sec + notes.maxOf { it.second }))
        for ((f, at) in notes) mixIn(out, bellNote(f, sec, decay, 0.5), n(at), 0.6f)
        return out
    }

    /** A square-ish wave (a few odd harmonics), so the lasers have bite without aliasing. */
    private fun sq(ph: Double) = sin(ph) + sin(3 * ph) / 3 + sin(5 * ph) / 5

    // ---- the guns ----

    /** The classic "pew": a falling square with a click of noise at the front. */
    private fun pew(r: Random, f0: Double, f1: Double, sec: Double): FloatArray {
        val out = FloatArray(n(sec))
        var ph = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SR
            ph += 2 * PI * (f1 + (f0 - f1) * exp(-t / (sec * 0.3))) / SR
            out[i] = (sq(ph) * env(i, 0.001, sec * 0.35)).toFloat()
        }
        for (i in 0 until n(0.004)) out[i] += (r.nextFloat() * 2 - 1) * 0.4f * (1 - i / n(0.004).toFloat())
        return out
    }

    private fun heavy(r: Random, v: Int): FloatArray {
        val out = pew(r, 900.0 - v * 80, 180.0, 0.16)
        var ph = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SR
            ph += 2 * PI * (60.0 + 140.0 * exp(-t / 0.02)) / SR
            out[i] += (sin(ph) * env(i, 0.001, 0.05)).toFloat()
        }
        return out
    }

    /** A sizzling beam: a buzzy chord with a shimmer on top. */
    private fun laser(r: Random): FloatArray {
        val out = FloatArray(n(0.12))
        val band = Band(4f).apply { tune(3200f) }
        var a = 0.0; var b = 0.0
        for (i in out.indices) {
            a += 2 * PI * 620 / SR; b += 2 * PI * 931 / SR
            val e = env(i, 0.003, 0.05)
            out[i] = ((sq(a) + sq(b)) * 0.4 * e).toFloat() + band.run(r.nextFloat() * 2 - 1) * e * 0.8f
        }
        return out
    }

    /** The railgun: a hard crack, then a ringing tail falling away. */
    private fun rail(r: Random): FloatArray {
        val out = FloatArray(n(0.5))
        var ph = 0.0
        val band = Band(2f)
        for (i in out.indices) {
            val t = i.toDouble() / SR
            if (i % 32 == 0) band.tune((6000 * exp(-t / 0.08) + 400).toFloat())
            ph += 2 * PI * (2400 * exp(-t / 0.05) + 140) / SR
            out[i] = (sin(ph) * env(i, 0.0005, 0.12)).toFloat() * 0.6f + band.run(r.nextFloat() * 2 - 1) * env(i, 0.0005, 0.09) * 1.5f
        }
        return out
    }

    private fun flak(r: Random): FloatArray {
        val out = FloatArray(n(0.12))
        val lp = Low(1800f)
        var ph = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SR
            ph += 2 * PI * (90.0 + 200.0 * exp(-t / 0.015)) / SR
            out[i] = lp.run(r.nextFloat() * 2 - 1) * env(i, 0.0005, 0.025) * 1.4f + (sin(ph) * env(i, 0.001, 0.04)).toFloat()
        }
        return out
    }

    private fun missile(r: Random): FloatArray {
        val out = FloatArray(n(0.3))
        val band = Band(1.5f)
        for (i in out.indices) {
            val x = i / out.size.toFloat()
            if (i % 32 == 0) band.tune(700f + 2200f * x)
            out[i] = band.run(r.nextFloat() * 2 - 1) * min(1f, x * 12) * (1 - x) * 1.8f
        }
        return out
    }

    /** An electric crackle: noise gated by a jittery square. */
    private fun zap(r: Random): FloatArray {
        val out = FloatArray(n(0.09))
        val band = Band(3f).apply { tune(2400f + r.nextFloat() * 1600) }
        var gate = 1f
        for (i in out.indices) {
            if (i % 90 == 0) gate = if (r.nextFloat() < 0.6f) 1f else 0.15f
            out[i] = band.run(r.nextFloat() * 2 - 1) * gate * env(i, 0.001, 0.035) * 2f
        }
        return out
    }

    // ---- hits and explosions ----

    /** A bullet sparking off armour: a tiny metallic tick. */
    private fun hit(r: Random, v: Int): FloatArray {
        val out = FloatArray(n(0.05))
        val f = 2200.0 + v * 500 + r.nextDouble() * 300
        for (i in out.indices) {
            val t = i.toDouble() / SR
            out[i] = (sin(2 * PI * f * t) * exp(-t / 0.006) + 0.5 * sin(2 * PI * f * 1.47 * t) * exp(-t / 0.004)).toFloat()
        }
        for (i in 0 until n(0.002)) out[i] += (r.nextFloat() * 2 - 1) * 0.5f
        return out
    }

    /** Stone cracking: a thump and a burst of gritty crackle. */
    private fun rock(r: Random): FloatArray {
        val out = FloatArray(n(0.32))
        val lp = Low(900f)
        var ph = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SR
            ph += 2 * PI * (70.0 + 90.0 * exp(-t / 0.03)) / SR
            out[i] = (sin(ph) * env(i, 0.001, 0.06)).toFloat() * 0.9f + lp.run(r.nextFloat() * 2 - 1) * env(i, 0.001, 0.08) * 1.4f
        }
        repeat(14) {
            val at = r.nextInt(out.size / 2)
            for (k in 0 until n(0.002)) if (at + k < out.size) out[at + k] += (r.nextFloat() * 2 - 1) * 0.5f * (1 - k / n(0.002).toFloat())
        }
        return out
    }

    /** An explosion: low-passed noise whose corner falls, over a sub thump. [sec] sets its size. */
    private fun boom(r: Random, sec: Double, pitch: Float): FloatArray {
        val out = FloatArray(n(sec))
        val lp = Low(4000f)
        val lp2 = Low(400f)
        var ph = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SR
            if (i % 64 == 0) lp.tune(((3500 * exp(-t / (sec * 0.2)) + 150) * pitch).toFloat())
            ph += 2 * PI * (40.0 + 80.0 * exp(-t / 0.05)) * pitch / SR
            val w = r.nextFloat() * 2 - 1
            out[i] = lp.run(w) * env(i, 0.001, sec * 0.3) * 1.8f + lp2.run(w) * env(i, 0.002, sec * 0.45) * 1.5f +
                (sin(ph) * env(i, 0.001, sec * 0.25)).toFloat()
        }
        return out
    }

    private fun bossBoom(r: Random): FloatArray {
        val out = boom(r, 2.4, 0.6f)
        mixIn(out, boom(r, 1.0, 0.9f), n(0.25), 0.7f)
        mixIn(out, boom(r, 1.0, 0.8f), n(0.6), 0.6f)
        mixIn(out, chord(listOf(261.63 to 0.0, 392.0 to 0.0, 523.25 to 0.0), 2.0, 0.8), n(0.1), 0.25f)
        return out
    }

    /** Taking a hit: a crunch and a falling alarm blip. */
    private fun hurt(r: Random): FloatArray {
        val out = boom(r, 0.35, 1.3f)
        var ph = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SR
            ph += 2 * PI * (520 - 300 * t / 0.35) / SR
            out[i] += (sq(ph) * env(i, 0.002, 0.08)).toFloat() * 0.5f
        }
        return out
    }

    private fun shield(): FloatArray {
        val out = FloatArray(n(0.4))
        var a = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SR
            a += 2 * PI * (300 + 900 * t) / SR
            out[i] = (sin(a + 2 * sin(a * 0.5)) * env(i, 0.01, 0.12)).toFloat()
        }
        return out
    }

    /** A bullet whistling past: a quick high shimmer. */
    private fun graze(r: Random, v: Int): FloatArray {
        val out = FloatArray(n(0.06))
        val band = Band(8f).apply { tune(5200f + v * 700) }
        for (i in out.indices) out[i] = band.run(r.nextFloat() * 2 - 1) * env(i, 0.004, 0.015) * 3f
        return out
    }

    // ---- pickups, specials, warnings ----

    private fun power(): FloatArray {
        val out = FloatArray(n(0.5))
        var ph = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SR
            // A rising arpeggio, stepped like an old console.
            val step = (t / 0.05).toInt().coerceAtMost(7)
            ph += 2 * PI * (440 * Math.pow(2.0, step * 4 / 12.0)) / SR
            out[i] = (sq(ph) * env(i, 0.003, 0.25)).toFloat() * 0.6f
        }
        return out
    }

    private fun special(r: Random): FloatArray {
        val out = FloatArray(n(1.2))
        var ph = 0.0
        val lp = Low(600f)
        for (i in out.indices) {
            val t = i.toDouble() / SR
            ph += 2 * PI * (80 + 900 * (1 - exp(-t / 0.15))) / SR
            out[i] = (sin(ph + sin(ph * 0.51) * 2) * env(i, 0.02, 0.35)).toFloat() * 0.7f + lp.run(r.nextFloat() * 2 - 1) * env(i, 0.05, 0.4) * 1.5f
        }
        mixIn(out, boom(r, 0.9, 0.7f), 0, 0.8f)
        return out
    }

    /** The boss alarm: two-tone klaxon, three times. */
    private fun warning(): FloatArray {
        val out = FloatArray(n(1.8))
        var ph = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SR
            val cyc = (t / 0.6) % 1.0
            val f = if (cyc < 0.5) 440.0 else 330.0
            ph += 2 * PI * f / SR
            val gate = if (cyc % 0.5 < 0.42) 1.0 else 0.0
            out[i] = (sq(ph) * gate * 0.5 * min(1.0, t / 0.01) * min(1.0, (1.8 - t) / 0.1)).toFloat()
        }
        return out
    }

    private fun whoosh(r: Random, sec: Double): FloatArray {
        val out = FloatArray(n(sec))
        val band = Band(2f)
        for (i in out.indices) {
            val x = i / out.size.toFloat()
            if (i % 32 == 0) band.tune(400f + 2600f * sin(x * PI.toFloat()))
            out[i] = band.run(r.nextFloat() * 2 - 1) * sin(x * PI.toFloat()) * 1.6f
        }
        return out
    }

    private fun tap(): FloatArray {
        val out = FloatArray(n(0.04))
        for (i in out.indices) {
            val t = i.toDouble() / SR
            out[i] = (sin(2 * PI * 1800 * t) * exp(-t / 0.006)).toFloat()
        }
        return out
    }

    private fun deny(): FloatArray {
        val out = FloatArray(n(0.25))
        var ph = 0.0
        for (i in out.indices) {
            ph += 2 * PI * (if (i < n(0.1)) 220.0 else 180.0) / SR
            out[i] = (sq(ph) * env(i, 0.003, 0.09)).toFloat() * 0.6f
        }
        return out
    }

    private fun fanfare(): FloatArray {
        val notes = listOf(523.25 to 0.0, 659.25 to 0.1, 783.99 to 0.2, 1046.5 to 0.32)
        val out = FloatArray(n(1.6))
        for ((f, at) in notes) {
            var ph = 0.0
            val start = n(at)
            for (i in 0 until n(1.2)) {
                if (start + i >= out.size) break
                ph += 2 * PI * f / SR
                out[start + i] += (sq(ph) * env(i, 0.004, 0.3)).toFloat() * 0.3f
            }
            mixIn(out, bellNote(f * 2, 1.0, 0.5, 0.4), start, 0.3f)
        }
        return out
    }
}
