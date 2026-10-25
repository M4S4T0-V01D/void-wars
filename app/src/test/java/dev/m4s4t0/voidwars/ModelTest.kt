package dev.m4s4t0.voidwars

import dev.m4s4t0.voidwars.gl.ShipMeshes
import dev.m4s4t0.voidwars.model.Boss
import dev.m4s4t0.voidwars.model.COSMETICS
import dev.m4s4t0.voidwars.model.Decal
import dev.m4s4t0.voidwars.model.Finish
import dev.m4s4t0.voidwars.model.MissionSpec
import dev.m4s4t0.voidwars.model.Mode
import dev.m4s4t0.voidwars.model.SWATCHES
import dev.m4s4t0.voidwars.model.Stories
import dev.m4s4t0.voidwars.model.Campaign
import dev.m4s4t0.voidwars.model.Engine
import dev.m4s4t0.voidwars.model.Hubs
import dev.m4s4t0.voidwars.model.Hull
import dev.m4s4t0.voidwars.model.Levels
import dev.m4s4t0.voidwars.model.Loadout
import dev.m4s4t0.voidwars.model.MAX_UPGRADE
import dev.m4s4t0.voidwars.model.Missions
import dev.m4s4t0.voidwars.model.Stage
import dev.m4s4t0.voidwars.model.Wings
import dev.m4s4t0.voidwars.model.upgradeCost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ModelTest {
    @Test
    fun levelsClimbSteadily() {
        assertEquals(1, Levels.of(0).first)
        assertEquals(2, Levels.of(Levels.need(1).toLong()).first)
        var last = 0
        for (l in 1 until Levels.MAX) { assertTrue(Levels.need(l) > last); last = Levels.need(l) }
        assertEquals(Levels.MAX, Levels.of(Long.MAX_VALUE / 2).first)
    }

    @Test
    fun partKeysRoundTrip() {
        for ((_, parts) in Loadout.ALL_PARTS) for (p in parts) assertEquals(p, Loadout.partOf(p.key))
    }

    @Test
    fun upgradesGetDearerAndStop() {
        for ((slot, parts) in Loadout.ALL_PARTS) for (p in parts) {
            val costs = (1 until MAX_UPGRADE).map { upgradeCost(p, it) }
            if (!slot.upgradable || costs.first() == null) continue
            costs.zipWithNext().forEach { (a, b) -> assertTrue(b!! > a!!) }
            assertEquals(null, upgradeCost(p, MAX_UPGRADE))
        }
    }

    @Test
    fun stagesAreStableAndEndInBosses() {
        for (s in Campaign.allStages) {
            val w = Campaign.waves(s)
            assertEquals(w, Campaign.waves(s))
            assertTrue(w.isNotEmpty())
            assertEquals(s.isBoss, w.any { it.boss != null })
            assertEquals(s, Stage.parse(s.id))
        }
        assertEquals(null, Stage(Campaign.SECTORS.size - 1, Campaign.STAGES - 1).next)
        assertEquals(Stage(5, 0), Stage(4, 5).next)
        assertEquals(Stage(1, 0), Stage(0, 5).next)
    }

    @Test
    fun starsAndRewards() {
        assertEquals(3, Campaign.stars(0.9f, 0.9f))
        assertEquals(2, Campaign.stars(0.9f, 0.1f))
        assertEquals(1, Campaign.stars(0.1f, 1f))
        val s = Stage(0, 5)
        assertTrue(Campaign.reward(s, 3, true, true).cores > 0)
        assertTrue(Campaign.reward(s, 1, true, false).credits > Campaign.reward(s, 1, false, false).credits)
    }

    @Test
    fun dailyMissionsAreThreeDifferentOnes() {
        repeat(50) {
            val m = Missions.daily(Random(it), 1 + it % 30, emptyMap())
            assertEquals(3, m.size)
            assertEquals(3, m.map { x -> x.id }.toSet().size)
        }
    }

    @Test
    fun everyMeshBuilds() {
        fun ok(name: String, d: FloatArray) {
            assertTrue("$name is empty", d.isNotEmpty())
            assertEquals("$name has a ragged vertex", 0, d.size % 7)
            assertEquals("$name has a stray vertex", 0, d.size / 7 % 3)
            assertTrue("$name has NaNs", d.none { it.isNaN() })
        }
        Hull.entries.forEach { ok("$it", ShipMeshes.hull(it)) }
        Wings.entries.forEach { ok("$it", ShipMeshes.wings(it)) }
        Engine.entries.forEach { ok("$it", ShipMeshes.engine(it)); assertNotNull(ShipMeshes.nozzles(it)) }
        Boss.entries.forEach { ok("$it", ShipMeshes.boss(it)) }
        ok("drone", ShipMeshes.drone()); ok("drone2", ShipMeshes.drone2()); ok("dart", ShipMeshes.dart())
        ok("gunship", ShipMeshes.gunship()); ok("spinner", ShipMeshes.spinner()); ok("mine", ShipMeshes.mine())
        ok("lancer", ShipMeshes.lancer()); ok("carrier", ShipMeshes.carrier())
        repeat(6) { ok("rock $it", ShipMeshes.rock(it)) }
    }

    @Test
    fun storyLinesAreCompleteAndTheirRewardsExist() {
        val keys = (Loadout.ALL_PARTS.values.flatten().map { it.key } + SWATCHES.map { it.key } + Finish.entries.map { it.key } +
            Decal.entries.map { it.key } + COSMETICS.map { it.key }).toSet()
        assertEquals(10, Mode.entries.size)
        for (m in Mode.entries) {
            val ch = Stories.all.getValue(m)
            assertEquals(m.name, Stories.CHAPTERS, ch.size)
            for (c in ch) {
                assertEquals(c, Stories.chapter(c.id))
                c.unlock?.let { assertTrue("${c.id} unlocks unknown $it", it in keys) }
                assertTrue(c.spec().goal > 0)
            }
        }
        assertTrue(Stories.LAST_LIGHT in keys)
    }

    @Test
    fun cosmeticsAreUniqueAndPlentiful() {
        assertTrue(COSMETICS.size >= 120)
        assertEquals(COSMETICS.size, COSMETICS.map { it.key }.toSet().size)
        assertEquals(20, Campaign.SECTORS.size)
    }

    @Test
    fun aHubForEverySector() {
        assertEquals(Campaign.SECTORS.size + 1, Hubs.all.size)
        assertEquals(Hubs.all.size, Hubs.all.map { it.title }.toSet().size)
        assertEquals(Hubs.all.size, Hubs.all.map { it.scenery }.toSet().size)
        for ((i, th) in Hubs.all.withIndex()) {
            assertEquals(i - 1, th.sector)
            assertTrue(th.title, th.planetSize > 0f && th.rocks >= 0 && th.traffic > 0)
        }
    }

    @Test
    fun quickMissionsAreDeterministic() {
        for (m in Mode.entries) for (sec in 0 until Campaign.SECTORS.size step 5) {
            assertEquals(MissionSpec.quick(m, sec, 42), MissionSpec.quick(m, sec, 42))
        }
    }
}
