package dev.m4s4t0.voidwars.audio

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.m4s4t0.voidwars.model.Game

/** Little jolts to go with the sounds: rich composed primitives where the motor has them, plain clicks otherwise. */
object Haptics {
    enum class Kind { TICK, CLICK, THUD, HIT, BOOM, BIG_BOOM, SUCCESS }

    private var vib: Vibrator? = null
    private var rich = false
    private var lastMs = 0L

    fun init(ctx: Context) {
        vib = (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        rich = vib?.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            VibrationEffect.Composition.PRIMITIVE_THUD,
        ) == true
    }

    /**
     * [strength] 0..1. Throttled so a hail of kills is a purr, not a solid buzz; the heavy kinds (being hit, a boss
     * going down) always get through.
     */
    fun buzz(kind: Kind, strength: Float = 1f) {
        val v = vib ?: return
        if (!Game.haptics || !v.hasVibrator()) return
        val now = System.nanoTime() / 1_000_000
        val gap = when (kind) { Kind.TICK -> 35; Kind.HIT, Kind.BIG_BOOM, Kind.SUCCESS -> 0; else -> 60 }
        if (now - lastMs < gap) return
        lastMs = now
        val s = strength.coerceIn(0.05f, 1f)
        val effect = if (rich) {
            val c = VibrationEffect.startComposition()
            when (kind) {
                Kind.TICK -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, s)
                Kind.CLICK -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, s)
                Kind.THUD -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, s)
                Kind.HIT -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s).addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, s, 30)
                Kind.BOOM -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, s * 0.7f)
                Kind.BIG_BOOM -> repeat(4) { c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, s * (1f - it * 0.2f), 40) }
                Kind.SUCCESS -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f).addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 90)
            }
            c.compose()
        } else {
            VibrationEffect.createPredefined(
                when (kind) {
                    Kind.TICK, Kind.CLICK -> VibrationEffect.EFFECT_TICK
                    Kind.THUD, Kind.BOOM, Kind.HIT, Kind.BIG_BOOM -> VibrationEffect.EFFECT_HEAVY_CLICK
                    Kind.SUCCESS -> VibrationEffect.EFFECT_DOUBLE_CLICK
                },
            )
        }
        runCatching { v.vibrate(effect) }
    }
}
