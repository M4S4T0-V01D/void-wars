package dev.m4s4t0.voidwars

import dev.m4s4t0.voidwars.audio.Haptics
import dev.m4s4t0.voidwars.audio.Snd
import dev.m4s4t0.voidwars.game.Hud
import dev.m4s4t0.voidwars.game.Sfx
import dev.m4s4t0.voidwars.game.World
import dev.m4s4t0.voidwars.game.WorldEvent
import dev.m4s4t0.voidwars.game.WorldListener
import dev.m4s4t0.voidwars.model.Campaign
import dev.m4s4t0.voidwars.model.Loadout
import dev.m4s4t0.voidwars.model.Primary
import dev.m4s4t0.voidwars.model.Special
import dev.m4s4t0.voidwars.model.Stage
import dev.m4s4t0.voidwars.model.Support
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Flies every stage headless, with a ship too tough to lose, and checks it runs to the end. */
class WorldTest {
    private object Quiet : Sfx {
        override fun play(s: Snd, gain: Float, pitch: Float, pan: Float, minGapMs: Long) = Unit
        override fun buzz(k: Haptics.Kind, strength: Float) = Unit
    }

    private class Log : WorldListener {
        val events = ArrayList<WorldEvent>()
        var huds = 0
        override fun hud(h: Hud) { huds++ }
        override fun event(e: WorldEvent) { events += e }
    }

    private fun fly(l: Loadout, stage: Stage?, seconds: Float, tough: Boolean, special: Boolean = false): Pair<World, Log> {
        val w = World(Quiet)
        val log = Log()
        w.listener = log
        w.W = 16f
        val base = l.stats { 5 }
        val stats = if (tough) base.copy(maxHp = 1_000_000, damage = 6f) else base
        w.start(l, stats, stage)
        w.trigger = true
        var t = 0f
        while (t < seconds && log.events.none { it is WorldEvent.Finished }) {
            w.frame(1f / 60f)
            if (special && w.charge >= 1f) w.special()
            if (log.events.lastOrNull() is WorldEvent.Died) w.giveUp()
            t += 1f / 60f
        }
        return w to log
    }

    @Test
    fun everyStageCanBeCleared() {
        for (s in Campaign.allStages) {
            val (w, log) = fly(Loadout(), s, Campaign.length(s) + 240f, tough = true)
            val fin = log.events.filterIsInstance<WorldEvent.Finished>().singleOrNull()
            assertTrue("stage ${s.title} never finished", fin != null)
            assertTrue("stage ${s.title} not cleared", fin!!.result.cleared)
            assertTrue("stage ${s.title} spawned nothing", fin.result.spawned > 10)
            assertTrue("stage ${s.title} sent no HUD", log.huds > 0)
            if (s.isBoss) {
                assertTrue("boss ${s.title} never warned", log.events.any { it is WorldEvent.BossWarning })
                assertTrue("boss ${s.title} never fell", log.events.any { it is WorldEvent.BossDown })
                assertEquals(1, fin.result.bosses)
            }
            assertTrue(w.bullets.n >= 0)
        }
    }

    @Test
    fun everyWeaponAndSpecialRuns() {
        for (p in Primary.entries) for (sp in Special.entries) {
            val l = Loadout(primary = p, special = sp, support = Support.entries[(p.ordinal + sp.ordinal) % Support.entries.size])
            val (_, log) = fly(l, Stage(2, 3), 40f, tough = true, special = true)
            assertTrue("$p + $sp", log.huds > 0)
        }
    }

    @Test
    fun aFragileShipGoesDownAndFinishes() {
        val (_, log) = fly(Loadout(), Stage(4, 5), 400f, tough = false)
        val died = log.events.filterIsInstance<WorldEvent.Died>().firstOrNull()
        assertTrue("never died", died != null)
        assertTrue(died!!.canRevive)
        val fin = log.events.filterIsInstance<WorldEvent.Finished>().single()
        assertTrue(!fin.result.cleared)
    }

    @Test
    fun gunsFireOnlyWithTheTriggerHeld() {
        val w = World(Quiet)
        w.W = 16f
        val l = Loadout()
        w.start(l, l.stats { 1 }, Stage(0, 0))
        repeat(180) { w.frame(1f / 60f) }
        assertEquals(0, w.shots.n)
        w.trigger = true
        repeat(30) { w.frame(1f / 60f) }
        assertTrue(w.shots.n > 0)
    }

    @Test
    fun theBackupGunSwapsIn() {
        val w = World(Quiet)
        w.W = 16f
        val l = Loadout(backup = Primary.RAIL)
        w.start(l, l.stats { 1 }, Stage(0, 0))
        assertEquals(Primary.PULSE, w.gun)
        assertEquals(Primary.RAIL, w.swapTo)
        w.swap()
        assertEquals(Primary.RAIL, w.gun)
        w.swap()
        assertEquals(Primary.PULSE, w.gun)
        // Fitting the backup as the main gun trades them over.
        assertEquals(Loadout(primary = Primary.RAIL, backup = Primary.PULSE), l.with(Primary.RAIL))
    }

    @Test
    fun theRiftKeepsComing() {
        val (_, log) = fly(Loadout(), null, 420f, tough = true)
        assertTrue("no rift boss in 7 minutes", log.events.any { it is WorldEvent.BossWarning })
    }
}
