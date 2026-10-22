package dev.m4s4t0.voidwars.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** A synthwave loop per sector, plus the menu, the boss fights and the Rift. Written by [Music] from a few choices. */
enum class Song(
    val bpm: Int,
    /** MIDI note of the key's root (natural minor). */
    val root: Int,
    /** Scale degrees of the four chords. */
    val chords: IntArray,
    val lead: Inst,
    val arp: Boolean,
    /** 0 = half-time and spacious, 1 = straight driving eighths, 2 = galloping sixteenths. */
    val drive: Int,
    val seed: Int,
) {
    MENU(92, 57, intArrayOf(0, 5, 2, 6), Inst.BELL, true, 0, 11),
    HANGAR(100, 62, intArrayOf(0, 3, 5, 4), Inst.PLUCK, true, 0, 17),
    RIM(118, 64, intArrayOf(0, 5, 2, 6), Inst.SAW, true, 1, 23),
    CRIMSON(126, 62, intArrayOf(0, 3, 5, 6), Inst.SQUARE, true, 1, 37),
    ION(132, 66, intArrayOf(0, 6, 5, 6), Inst.SAW, true, 2, 41),
    DEAD(110, 60, intArrayOf(0, 5, 3, 4), Inst.BELL, false, 1, 53),
    HEART(138, 63, intArrayOf(0, 1, 5, 6), Inst.SQUARE, true, 2, 67),
    BOSS(148, 64, intArrayOf(0, 1, 0, 6), Inst.SAW, true, 2, 71),
    RIFT(140, 61, intArrayOf(0, 5, 6, 4), Inst.SQUARE, true, 2, 83),
    ;

    companion object {
        fun forSector(s: Int) = listOf(RIM, CRIMSON, ION, DEAD, HEART)[s.coerceIn(0, 4)]
    }
}

enum class Inst { SAW, SQUARE, BELL, PLUCK, PAD, BASS }

/**
 * Writes a [Song] into a stereo loop: a lead built from a little motif (A A' B A''), a lush pad, an arpeggio, a
 * pulsing bass and a drum machine, all synthesized. Sixteen bars, rendered at half the mixer's rate.
 */
object Music {
    const val RATE = SoundBank.SR / 2
    private const val BARS = 16
    private val MINOR = intArrayOf(0, 2, 3, 5, 7, 8, 10)

    private class Buf(val n: Int) {
        val l = FloatArray(n)
        val r = FloatArray(n)
        fun add(i: Int, v: Float, pan: Float) {
            val k = ((i % n) + n) % n
            l[k] += v * (1f - pan) * 0.5f
            r[k] += v * (1f + pan) * 0.5f
        }
    }

    /** The loop, interleaved stereo, 16-bit. */
    fun render(song: Song): ShortArray {
        val rnd = Random(song.seed)
        val beat = 60f / song.bpm
        val n = (BARS * 4 * beat * RATE).toInt()
        val b = Buf(n)
        fun at(beats: Float) = (beats * beat * RATE).toInt()
        fun freq(midi: Float) = 440f * 2f.pow((midi - 69f) / 12f)
        fun step(s: Int) = song.root + MINOR[((s % 7) + 7) % 7] + 12 * Math.floorDiv(s, 7)
        fun chordTones(bar: Int): IntArray { val d = song.chords[bar % 4]; return intArrayOf(d, d + 2, d + 4) }

        // ---- lead ----
        data class N(val slot: Int, val len: Int, val step: Int)
        val rhythms = listOf(
            intArrayOf(1, 0, 1, 0, 1, 1, 1, 0), intArrayOf(1, 1, 1, 0, 1, 0, 1, 0), intArrayOf(1, 0, 0, 1, 0, 0, 1, 0),
            intArrayOf(1, 0, 1, 1, 1, 0, 0, 0), intArrayOf(1, 0, 0, 0, 1, 1, 1, 1), intArrayOf(1, 0, 0, 1, 0, 0, 1, 1),
        )
        fun motif(bars: Int, startBar: Int, from: Int): List<List<N>> {
            var cur = from
            return (0 until bars).map { k ->
                val rh = rhythms[rnd.nextInt(rhythms.size)]
                val onsets = rh.indices.filter { rh[it] == 1 }
                val tones = chordTones(startBar + k)
                onsets.mapIndexed { j, slot ->
                    val len = (onsets.getOrNull(j + 1) ?: 8) - slot
                    cur = if (slot % 4 == 0) {
                        tones.flatMap { listOf(it, it + 7) }.filter { it in 0..12 }.minBy { abs(it - cur) + rnd.nextFloat() }
                    } else {
                        var s = cur + listOf(-2, -1, -1, 1, 1, 2)[rnd.nextInt(6)]
                        if (s < 0) s += 2
                        if (s > 11) s -= 2
                        s
                    }
                    N(slot, len, cur)
                }
            }
        }
        val a = motif(2, 0, 4)
        val a3 = motif(1, 2, a[1].last().step)
        val bSec = motif(3, 0, 7)
        fun cadence(bar: Int, home: Boolean): List<N> {
            val t = if (home) 7 else chordTones(bar)[rnd.nextInt(3)]
            return listOf(N(0, 2, t + 1), N(2, 2, t + (if (home) 2 else 0)), N(4, 4, t))
        }
        // The first phrase is an intro (pad and drums only), so a stage starts with a build.
        val phrases = listOf(null, a + a3 + listOf(cadence(3, true)), bSec + listOf(cadence(3, false)), a + a3 + listOf(cadence(3, true)))
        for ((p, bars) in phrases.withIndex()) {
            if (bars == null) continue
            for ((k, notes) in bars.withIndex()) {
                val bar = p * 4 + k
                for (nt in notes) {
                    val t = bar * 4 + nt.slot * 0.5f
                    val dur = nt.len * 0.5f * beat * 0.92f
                    note(b, at(t), freq(step(nt.step).toFloat()), dur, song.lead, 0.26f, -0.12f)
                    // A delay echo, a dotted eighth later, off to the other side.
                    note(b, at(t + 0.75f), freq(step(nt.step).toFloat()), dur, song.lead, 0.08f, 0.5f)
                    if (p == 3) note(b, at(t), freq(step(nt.step + 7).toFloat()), dur, Inst.BELL, 0.06f, 0.3f)
                }
            }
        }

        // ---- pad, arpeggio, bass ----
        for (bar in 0 until BARS) {
            val tones = chordTones(bar).map { step(it) - 12f }
            val t0 = bar * 4f
            for ((i, m) in tones.withIndex()) note(b, at(t0), freq(m), 4 * beat, Inst.PAD, 0.07f, -0.6f + i * 0.6f)
            if (song.arp && bar >= 2) for (s in 0 until 16) {
                val m = tones[listOf(0, 1, 2, 1)[s % 4]] + if (s % 8 >= 4) 12 else 0
                note(b, at(t0 + s * 0.25f), freq(m + 12f), 0.12f * beat, Inst.PLUCK, if (s % 4 == 0) 0.07f else 0.045f, if (s % 2 == 0) 0.5f else -0.5f)
            }
            val bass = step(song.chords[bar % 4]) - 24f
            when (song.drive) {
                0 -> { note(b, at(t0), freq(bass), 1.8f * beat, Inst.BASS, 0.3f, 0f); note(b, at(t0 + 2.5f), freq(bass), 1.2f * beat, Inst.BASS, 0.22f, 0f) }
                1 -> for (s in 0 until 8) note(b, at(t0 + s * 0.5f), freq(bass + if (s == 7) 12f else 0f), 0.4f * beat, Inst.BASS, if (s % 2 == 0) 0.28f else 0.2f, 0f)
                else -> for (s in 0 until 16) if (s % 4 != 1) note(b, at(t0 + s * 0.25f), freq(bass + if (s % 8 == 6) 12f else 0f), 0.2f * beat, Inst.BASS, if (s % 4 == 0) 0.28f else 0.18f, 0f)
            }
        }

        // ---- drums ----
        for (bar in 0 until BARS) {
            val t0 = bar * 4f
            val intro = bar < 4
            if (song.drive == 0) {
                kick(b, at(t0), 0.4f); kick(b, at(t0 + 2.5f), 0.25f)
                snare(b, at(t0 + 2f), 0.22f, rnd)
                for (s in 0 until 8) hat(b, at(t0 + s * 0.5f), if (s % 2 == 1) 0.05f else 0.03f, rnd)
            } else {
                for (k in 0 until 4) kick(b, at(t0 + k), if (intro && k % 2 == 1) 0f else 0.45f)
                if (!intro || bar == 3) { snare(b, at(t0 + 1f), 0.26f, rnd); snare(b, at(t0 + 3f), 0.26f, rnd) }
                val hats = if (song.drive == 2) 16 else 8
                for (s in 0 until hats) hat(b, at(t0 + s * 4f / hats), if ((s * 8 / hats) % 2 == 1) 0.06f else 0.035f, rnd)
                // A snare roll into each section.
                if (bar % 4 == 3) for (s in 0 until 4) snare(b, at(t0 + 3f + s * 0.25f), 0.08f + s * 0.04f, rnd)
            }
        }

        var peak = 1e-6f
        for (i in 0 until n) peak = max(peak, max(abs(b.l[i]), abs(b.r[i])))
        val g = 0.9f / peak * 32767f
        return ShortArray(n * 2) { i -> ((if (i % 2 == 0) b.l[i / 2] else b.r[i / 2]) * g).toInt().coerceIn(-32767, 32767).toShort() }
    }

    private fun note(b: Buf, start: Int, f: Float, dur: Float, inst: Inst, vel: Float, pan: Float) {
        if (vel <= 0f) return
        val sr = RATE.toFloat()
        val (attack, release, tail) = when (inst) {
            Inst.SAW -> Triple(0.01f, 0.15f, 0f)
            Inst.SQUARE -> Triple(0.005f, 0.1f, 0f)
            Inst.BELL -> Triple(0.002f, 0f, 1.2f)
            Inst.PLUCK -> Triple(0.002f, 0f, 0.18f)
            Inst.PAD -> Triple(0.6f, 0.8f, 0f)
            Inst.BASS -> Triple(0.004f, 0.05f, 0f)
        }
        val decaying = tail > 0f
        val len = ((if (decaying) tail * 1.6f else dur + release) * sr).toInt()
        val hold = dur * sr
        val w = 2f * PI.toFloat() * f / sr
        var ph = 0f
        var ph2 = 0f
        var lp = 0f
        // One-pole low-pass to round off the saws; brighter at the start of the note (a filter "pluck").
        for (i in 0 until len) {
            val t = i / sr
            val env = min(1f, t / attack) * if (decaying) exp(-t / (tail * 0.35f)) else if (i < hold) 1f else exp(-(i - hold) / (release * sr * 0.35f))
            if (env < 1e-4f && i > 100) break
            val c1 = (ph / (2f * PI.toFloat())) % 1f
            val c2 = (ph2 / (2f * PI.toFloat())) % 1f
            val raw = when (inst) {
                Inst.SAW -> (2f * c1 - 1f) * 0.5f + (2f * c2 - 1f) * 0.5f
                Inst.SQUARE -> (if (c1 < 0.5f) 0.5f else -0.5f) + (if (c2 < 0.3f) 0.25f else -0.1f)
                Inst.BELL -> sin(ph + 1.6f * sin(ph * 3.5f) * exp(-t * 3f)) * 0.8f
                Inst.PLUCK -> if (c1 < 0.5f) 0.6f else -0.6f
                Inst.PAD -> (2f * c1 - 1f) * 0.4f + (2f * c2 - 1f) * 0.4f
                Inst.BASS -> (2f * c1 - 1f) * 0.6f + sin(ph) * 0.6f
            }
            val cut = when (inst) {
                Inst.SAW -> 0.12f + 0.3f * exp(-t * 6f)
                Inst.SQUARE -> 0.18f + 0.2f * exp(-t * 8f)
                Inst.PLUCK -> 0.1f + 0.4f * exp(-t * 30f)
                Inst.PAD -> 0.05f
                Inst.BASS -> 0.05f + 0.15f * exp(-t * 25f)
                Inst.BELL -> 1f
            }
            lp += (raw - lp) * cut
            b.add(start + i, lp * env * vel, pan)
            ph += w
            ph2 += w * 1.006f
            if (ph > 2f * PI.toFloat() * 4096f) { ph -= 2f * PI.toFloat() * 4096f; ph2 -= 2f * PI.toFloat() * 4096f }
        }
    }

    private fun kick(b: Buf, start: Int, vel: Float) {
        if (vel <= 0f) return
        val sr = RATE.toFloat()
        var ph = 0f
        for (i in 0 until (0.28f * sr).toInt()) {
            val t = i / sr
            ph += 2f * PI.toFloat() * (45f + 140f * exp(-t * 30f)) / sr
            b.add(start + i, sin(ph) * exp(-t * 11f) * vel, 0f)
        }
    }

    /** A gated, reverby synth snare: noise and a tone body. */
    private fun snare(b: Buf, start: Int, vel: Float, rnd: Random) {
        val sr = RATE.toFloat()
        var lp = 0f
        for (i in 0 until (0.3f * sr).toInt()) {
            val t = i / sr
            val x = rnd.nextFloat() * 2f - 1f
            lp += (x - lp) * 0.5f
            val body = sin(2f * PI.toFloat() * 190f * t) * exp(-t * 30f)
            b.add(start + i, (lp * exp(-t * 12f) + body * 0.6f) * vel, -0.05f)
        }
    }

    private fun hat(b: Buf, start: Int, vel: Float, rnd: Random) {
        val sr = RATE.toFloat()
        var lp = 0f
        for (i in 0 until (0.05f * sr).toInt()) {
            val t = i / sr
            val x = rnd.nextFloat() * 2f - 1f
            lp += (x - lp) * 0.2f
            b.add(start + i, (x - lp) * exp(-t * 70f) * vel, 0.3f)
        }
    }
}
