package dev.m4s4t0.voidwars.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import dev.m4s4t0.voidwars.model.Game
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * A small low-latency mixer: the sounds are synthesized once at start-up (SoundBank), then played by a thread that
 * mixes up to [VOICES] at a time into one AudioTrack. Safe to call from any thread.
 */
object Audio {
    private const val VOICES = 28
    private const val BLOCK = 256

    private class Voice { var data: FloatArray? = null; var pos = 0f; var rate = 1f; var gain = 0f; var pan = 0f }
    private class Req(val data: FloatArray, val rate: Float, val gain: Float, val pan: Float)

    @Volatile private var bank: Map<Snd, Array<FloatArray>> = emptyMap()
    private val queue = ConcurrentLinkedQueue<Req>()
    private val voices = Array(VOICES) { Voice() }
    private val rnd = Random(5)
    @Volatile private var running = false
    private var thread: Thread? = null
    private val last = LongArray(Snd.entries.size)

    /** Ducks everything but the music (the pause menu). */
    @Volatile var muffled = false
    private var sfxGain = 1f

    /** The tune to play (null for quiet). Rendered on its own thread the first time, then crossfaded in. */
    @Volatile var song: Song? = null
        set(v) { field = v; v?.let(::prepare) }
    private val loops = ConcurrentHashMap<Song, ShortArray>()
    private val rendering = java.util.Collections.newSetFromMap(ConcurrentHashMap<Song, Boolean>())
    private var playing: Song? = null
    private var playPos = 0f
    private var playGain = 0f
    private var fading: Song? = null
    private var fadePos = 0f
    private var fadeGain = 0f

    /** Renders [s] ahead of time (the next stage's tune while the briefing is up). */
    fun prepare(s: Song) {
        if (loops.containsKey(s) || !rendering.add(s)) return
        Thread({
            val data = Music.render(s)
            // Keep a couple of loops in memory: the one playing and the one being faded to.
            if (loops.size >= 3) loops.keys.filter { it != song && it != playing }.forEach { loops.remove(it) }
            loops[s] = data
            rendering.remove(s)
        }, "VoidWarsMusic").apply { priority = Thread.NORM_PRIORITY - 1; start() }
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread({
            if (bank.isEmpty()) bank = SoundBank.build()
            loop()
        }, "VoidWarsAudio").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    /**
     * Plays [s]. [pitch] shifts it (1 = as made, with a little random spread on top), [gain] 0..1, [pan] -1..1.
     * The same sound can't restart within [minGapMs], so a storm of shots doesn't turn into a buzz.
     */
    fun play(s: Snd, gain: Float = 1f, pitch: Float = 1f, pan: Float = 0f, minGapMs: Long = 25) {
        if (!Game.sounds || !running) return
        val b = bank[s] ?: return
        val now = System.nanoTime() / 1_000_000
        synchronized(last) {
            if (now - last[s.ordinal] < minGapMs) return
            last[s.ordinal] = now
        }
        val v = b[rnd.nextInt(b.size)]
        val spread = 1f + (rnd.nextFloat() - 0.5f) * 0.06f
        queue.add(Req(v, pitch * spread, (gain * Game.volume).coerceIn(0f, 1.5f), pan.coerceIn(-1f, 1f)))
    }

    private fun loop() {
        val sr = SoundBank.SR
        val min = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(maxOf(min, BLOCK * 8 * 4))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        val out = FloatArray(BLOCK * 2)
        try {
            while (running) {
                while (true) {
                    val r = queue.poll() ?: break
                    val v = voices.firstOrNull { it.data == null } ?: voices.minBy { it.gain }
                    v.data = r.data; v.pos = 0f; v.rate = r.rate; v.gain = r.gain; v.pan = r.pan
                }
                out.fill(0f)
                val target = if (muffled) 0.25f else 1f
                for (v in voices) {
                    val d = v.data ?: continue
                    val gl = v.gain * (1f - v.pan) * 0.5f * 1.3f
                    val gr = v.gain * (1f + v.pan) * 0.5f * 1.3f
                    var p = v.pos
                    var g = sfxGain
                    for (i in 0 until BLOCK) {
                        val k = p.toInt()
                        if (k + 1 >= d.size) { v.data = null; break }
                        val f = p - k
                        val s = (d[k] + (d[k + 1] - d[k]) * f) * g
                        out[i * 2] += s * gl
                        out[i * 2 + 1] += s * gr
                        p += v.rate
                        g += (target - g) * 0.0005f
                    }
                    v.pos = p
                }
                sfxGain += (target - sfxGain) * min(1f, BLOCK * 0.0005f)
                mixMusic(out)
                // Gentle limiter.
                for (i in out.indices) {
                    val x = out[i]
                    out[i] = if (x > 0.8f) 0.8f + (x - 0.8f) / (1f + (x - 0.8f) * 5f) else if (x < -0.8f) -0.8f + (x + 0.8f) / (1f - (x + 0.8f) * 5f) else x
                }
                track.write(out, 0, out.size, AudioTrack.WRITE_BLOCKING)
            }
        } finally {
            track.stop()
            track.release()
        }
    }

    private fun mixMusic(out: FloatArray) {
        val want = song?.takeIf { Game.music && loops.containsKey(it) }
        if (want != playing) {
            // The old tune fades out while the new one comes in from the top.
            if (playing != null && playGain > 0.01f) { fading = playing; fadePos = playPos; fadeGain = playGain }
            playing = want; playPos = 0f; playGain = 0f
        }
        val rate = 1f / (SoundBank.SR * 1.2f)
        fading?.let { f ->
            val d = loops[f]
            val dg = -min(rate * 1.5f, fadeGain / BLOCK)
            if (d == null || fadeGain <= 1e-4f) fading = null
            else { fadePos = mixLoop(out, d, fadePos, fadeGain, dg); fadeGain += dg * BLOCK }
        }
        val p = playing ?: return
        val d = loops[p] ?: return
        val target = 0.36f * Game.musicVolume
        val dg = if (playGain < target) min(rate, (target - playGain) / BLOCK) else -min(rate, (playGain - target) / BLOCK)
        playPos = mixLoop(out, d, playPos, playGain, dg)
        playGain += dg * BLOCK
    }

    /** Adds the stereo loop [d] (half rate) to [out] from [pos], with [gain] moving by [dg] a sample. Returns the new pos. */
    private fun mixLoop(out: FloatArray, d: ShortArray, pos: Float, gain: Float, dg: Float): Float {
        val frames = d.size / 2
        var p = pos
        var g = gain
        for (i in 0 until BLOCK) {
            val k = p.toInt()
            val f = p - k
            val k1 = if (k + 1 >= frames) 0 else k + 1
            val l = (d[k * 2] + (d[k1 * 2] - d[k * 2]) * f) / 32767f
            val r = (d[k * 2 + 1] + (d[k1 * 2 + 1] - d[k * 2 + 1]) * f) / 32767f
            out[i * 2] += l * g
            out[i * 2 + 1] += r * g
            g = max(0f, g + dg)
            p += 0.5f
            if (p >= frames) p -= frames
        }
        return p
    }
}
