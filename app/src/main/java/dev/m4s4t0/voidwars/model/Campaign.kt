package dev.m4s4t0.voidwars.model

import kotlin.math.min
import kotlin.random.Random

/** Everything that flies at you. [hp] is at sector 1; [credits] is what it drops on average. */
enum class Foe(val title: String, val hp: Float, val radius: Float, val score: Int, val credits: Int) {
    ROCK_S("Pebble", 6f, 0.45f, 10, 1),
    ROCK_M("Rock", 26f, 0.85f, 30, 2),
    ROCK_L("Boulder", 80f, 1.45f, 80, 4),
    DRONE("Drone", 10f, 0.5f, 25, 1),
    DART("Dart", 16f, 0.55f, 40, 2),
    GUNSHIP("Gunship", 70f, 0.95f, 120, 5),
    SPINNER("Spinner", 90f, 0.9f, 150, 6),
    MINE("Mine", 22f, 0.6f, 50, 2),
    LANCER("Lancer", 60f, 0.85f, 140, 5),
    CARRIER("Carrier", 520f, 2.0f, 900, 30),
    BOSS("Boss", 2600f, 2.6f, 5000, 150),
}

/** The end-of-sector bosses. Each has its own ship and its own patterns (see the engine's Bosses). */
enum class Boss(val title: String, val subtitle: String) {
    WARDEN("The Warden", "Gatekeeper of the Rim"),
    HYDRA("Hydra", "Three heads, one temper"),
    TEMPEST("Tempest", "The storm that thinks"),
    REVENANT("Revenant", "Captain of the dead fleet"),
    VOID_HEART("Void Heart", "What waits at the centre"),
}

/** How a wave enters. */
enum class Formation { ROCKS, SINE_LINE, V_WING, DIVE, GUN_PAIR, SPIN, MINES, LANCE, SWARM, ELITE, BOSS }

/** One wave: at [at] seconds, [count] of a [formation] around [y] (-1..1, the screen's height). */
data class Wave(val at: Float, val formation: Formation, val count: Int, val y: Float, val seed: Int, val boss: Boss? = null)

/** A region of space: its colours (ARGB), its level gate, the enemies that live there and its boss. */
data class Sector(
    val title: String,
    val subtitle: String,
    val nebulaA: Long,
    val nebulaB: Long,
    val planet: Long,
    val gate: Int,
    val boss: Boss,
    val roster: Map<Formation, Int>,
)

data class Stage(val sector: Int, val index: Int) {
    val id get() = "$sector-$index"
    val isBoss get() = index == Campaign.STAGES - 1
    val hasElite get() = index == 2 || index == 4
    /** 0 at the first stage, about 5.8 at the last. */
    val difficulty get() = sector + index * 0.17f
    val title get() = "${sector + 1}-${index + 1}"
    val next: Stage? get() = when {
        index + 1 < Campaign.STAGES -> Stage(sector, index + 1)
        sector + 1 < Campaign.SECTORS.size -> Stage(sector + 1, 0)
        else -> null
    }

    companion object {
        fun parse(id: String): Stage? = id.split('-').takeIf { it.size == 2 }?.let { (a, b) ->
            val s = a.toIntOrNull() ?: return null
            val i = b.toIntOrNull() ?: return null
            if (s in Campaign.SECTORS.indices && i in 0 until Campaign.STAGES) Stage(s, i) else null
        }
    }
}

/** What a run pays out at the end. */
data class Reward(val credits: Int, val xp: Int, val cores: Int)

object Campaign {
    const val STAGES = 6
    /** The endless mode opens once the first boss is down. */
    val RIFT_UNLOCK = Stage(0, STAGES - 1)

    val SECTORS = listOf(
        Sector(
            "Outer Rim", "Where the rocks get thick", 0xFF2B1B6E, 0xFF0E5E8A, 0xFFC08A4A, 1, Boss.WARDEN,
            mapOf(Formation.ROCKS to 5, Formation.SINE_LINE to 3, Formation.V_WING to 2, Formation.DIVE to 1),
        ),
        Sector(
            "Crimson Belt", "Pirate country", 0xFF6E1630, 0xFF8A4A0E, 0xFFE0603A, 4, Boss.HYDRA,
            mapOf(Formation.ROCKS to 3, Formation.DIVE to 3, Formation.GUN_PAIR to 2, Formation.V_WING to 2, Formation.MINES to 1),
        ),
        Sector(
            "Ion Storm", "Lightning on every side", 0xFF0E5A6E, 0xFF2A7A2A, 0xFF7AE0FF, 8, Boss.TEMPEST,
            mapOf(Formation.SPIN to 3, Formation.SINE_LINE to 2, Formation.GUN_PAIR to 2, Formation.MINES to 2, Formation.LANCE to 1, Formation.SWARM to 2),
        ),
        Sector(
            "Dead Fleet", "A graveyard that shoots back", 0xFF3A3A4A, 0xFF7A5A1A, 0xFF9A9AAA, 12, Boss.REVENANT,
            mapOf(Formation.GUN_PAIR to 3, Formation.LANCE to 3, Formation.SPIN to 2, Formation.DIVE to 2, Formation.SWARM to 2, Formation.ROCKS to 1),
        ),
        Sector(
            "Void Heart", "The end of everything", 0xFF14041E, 0xFF7A0E6A, 0xFF2A0A3A, 16, Boss.VOID_HEART,
            mapOf(
                Formation.SPIN to 3, Formation.LANCE to 3, Formation.GUN_PAIR to 2, Formation.SWARM to 3, Formation.MINES to 2,
                Formation.DIVE to 2, Formation.ROCKS to 1, Formation.V_WING to 1,
            ),
        ),
    )

    val allStages: List<Stage> = SECTORS.indices.flatMap { s -> (0 until STAGES).map { Stage(s, it) } }

    /** Enemy toughness and bullet speed for a difficulty. */
    fun hpScale(d: Float) = 1f + 0.42f * d + 0.04f * d * d
    fun bulletScale(d: Float) = 1f + 0.07f * min(d, 6f)
    fun fireScale(d: Float) = 1f + 0.12f * min(d, 7f)

    /** Seconds of waves before the stage's end (or its boss). */
    fun length(s: Stage) = 58f + s.index * 6f + s.sector * 4f

    /** The stage's waves, the same every time it's flown. */
    fun waves(s: Stage): List<Wave> {
        val rnd = Random(s.sector * 1000 + s.index * 17 + 7)
        val sector = SECTORS[s.sector]
        val end = length(s)
        val out = ArrayList<Wave>()
        var t = 2.2f
        var last: Formation? = null
        while (t < end) {
            var f = pick(sector.roster, rnd)
            if (f == last) f = pick(sector.roster, rnd)
            last = f
            out += wave(t, f, s.difficulty, rnd)
            t += gap(f) * (1f - 0.04f * s.difficulty) * (0.85f + rnd.nextFloat() * 0.3f)
        }
        if (s.hasElite) out += Wave(end * 0.6f, Formation.ELITE, 1, 0f, rnd.nextInt())
        if (s.isBoss) out += Wave(end + 2.5f, Formation.BOSS, 1, 0f, rnd.nextInt(), sector.boss)
        out.sortBy { it.at }
        return out
    }

    /** The Rift's next wave after [t] seconds: everything, faster and harder as it goes, and a boss every few minutes. */
    fun riftWave(t: Float, n: Int, rnd: Random): Wave {
        val d = riftDifficulty(t)
        if (n > 0 && n % 28 == 0) return Wave(t, Formation.BOSS, 1, 0f, rnd.nextInt(), Boss.entries[(n / 28 - 1) % Boss.entries.size])
        if (n > 0 && n % 9 == 0) return Wave(t, Formation.ELITE, 1, 0f, rnd.nextInt())
        val sector = SECTORS[min(SECTORS.size - 1, (d / 1.2f).toInt())]
        return wave(t, pick(sector.roster, rnd), d, rnd)
    }

    fun riftDifficulty(t: Float) = 0.4f + t / 55f

    /** Seconds until the Rift's next wave. */
    fun riftGap(f: Formation, t: Float) = if (f == Formation.BOSS) 6f else gap(f) * (1f - min(0.45f, t / 600f))

    private fun wave(t: Float, f: Formation, d: Float, rnd: Random): Wave {
        val grow = (d * 0.6f).toInt()
        val count = when (f) {
            Formation.ROCKS -> 5 + grow + rnd.nextInt(3)
            Formation.SINE_LINE -> 5 + grow
            Formation.V_WING -> 5 + (grow / 2) * 2
            Formation.DIVE -> 3 + grow / 2
            Formation.GUN_PAIR -> 2 + if (d > 3f) 1 else 0
            Formation.SPIN -> 1 + if (d > 2.5f) 1 else 0
            Formation.MINES -> 4 + grow
            Formation.LANCE -> 2 + if (d > 3.5f) 1 else 0
            Formation.SWARM -> 8 + grow * 2
            Formation.ELITE, Formation.BOSS -> 1
        }
        return Wave(t, f, count, rnd.nextFloat() * 1.3f - 0.65f, rnd.nextInt())
    }

    private fun gap(f: Formation) = when (f) {
        Formation.ROCKS -> 4.2f
        Formation.SINE_LINE, Formation.V_WING -> 4.6f
        Formation.DIVE -> 3.6f
        Formation.GUN_PAIR -> 6.5f
        Formation.SPIN -> 7.5f
        Formation.MINES -> 4.5f
        Formation.LANCE -> 6.5f
        Formation.SWARM -> 5.5f
        Formation.ELITE -> 10f
        Formation.BOSS -> 0f
    }

    private fun pick(roster: Map<Formation, Int>, rnd: Random): Formation {
        var r = rnd.nextInt(roster.values.sum())
        for ((f, w) in roster) {
            if (r < w) return f
            r -= w
        }
        return roster.keys.first()
    }

    /**
     * Stars for a cleared stage: one for clearing it, two for finishing with at least 60% of the hull, three for that and
     * shooting down at least 80% of what came.
     */
    fun stars(hullLeft: Float, killRatio: Float): Int = when {
        hullLeft >= 0.6f && killRatio >= 0.8f -> 3
        hullLeft >= 0.6f -> 2
        else -> 1
    }

    /**
     * The stage-clear payout: the first clear pays double, a boss's first fall gives cores, and so does the first time a
     * stage is flown for three stars.
     */
    fun reward(s: Stage, stars: Int, firstClear: Boolean, firstThreeStars: Boolean): Reward {
        val base = 90 + (s.difficulty * 45).toInt() + if (s.isBoss) 250 else 0
        val credits = (base * (1f + 0.25f * (stars - 1)) * if (firstClear) 2f else 1f).toInt()
        val xp = 60 + (s.difficulty * 30).toInt() + if (s.isBoss) 150 else 0
        val cores = (if (firstClear && s.isBoss) 5 + s.sector * 2 else 0) + if (firstThreeStars) 2 else 0
        return Reward(credits, if (firstClear) xp * 2 else xp, cores)
    }

    /** The Rift pays by score and by how long you lasted. */
    fun riftReward(score: Long, seconds: Float): Reward =
        Reward((score / 90).toInt() + (seconds * 1.2f).toInt(), 40 + (seconds * 1.5f).toInt(), (seconds / 150f).toInt())
}

/** Pilot levels: how much XP each takes. */
object Levels {
    const val MAX = 50

    fun need(level: Int) = 120 + (level - 1) * 70 + (level - 1) * (level - 1) * 6

    /** (level, XP into it, XP it needs). */
    fun of(xp: Long): Triple<Int, Long, Long> {
        var lvl = 1
        var left = xp
        while (lvl < MAX && left >= need(lvl)) {
            left -= need(lvl)
            lvl++
        }
        return Triple(lvl, left, need(lvl).toLong())
    }
}
