package dev.m4s4t0.voidwars.model

import java.time.LocalDate
import kotlin.math.max

import kotlin.random.Random

/**
 * The ten mission types besides the campaign and the Rift. The first three fly on the side-scrolling rails like the
 * campaign; the rest ([open]) are flown in open space, in any direction. [level] is the pilot level a mode opens at.
 */
enum class Mode(val title: String, val blurb: String, val icon: String, val open: Boolean, val level: Int, val unit: String) {
    SURVIVAL("Survival", "Hold out against an endless swarm", "shield", false, 3, "s"),
    BOSS_RUSH("Boss Rush", "Every boss, one after another", "bosses", false, 12, "bosses"),
    BLITZ("Blitz", "Score as much as you can, fast", "combo", false, 7, "pts"),
    EXPLORATION("Exploration", "Chart the beacons in deep space", "beacon", true, 2, "beacons"),
    BOUNTY("Bounty", "Hunt down marked outlaws", "target", true, 5, "targets"),
    ESCORT("Escort", "Bring the convoy home alive", "escort", true, 9, "convoy"),
    SALVAGE("Salvage", "Recover pods from the wreckage", "salvage", true, 4, "pods"),
    MINING("Mining", "Crack rocks and haul ore", "ore", true, 6, "ore"),
    DEFENSE("Defense", "Keep the outpost standing", "defense", true, 10, "s"),
    ACE("Ace Duel", "One-on-one with the best pilots", "ace", true, 14, "aces");

    /** Whether the goal counts down time rather than things. */
    val timed get() = this == SURVIVAL || this == DEFENSE
}

/**
 * One mission to fly: what kind, where (its sector's colours, enemies and scaling), how hard, the [goal] to hit and
 * the time it allows ([seconds], 0 for none). It may be a story chapter ([storyId]) or a daily contract ([contractId]).
 */
data class MissionSpec(
    val mode: Mode,
    val sector: Int,
    val difficulty: Float,
    val goal: Int,
    val seconds: Float,
    val seed: Int,
    val title: String,
    val storyId: String? = null,
    val contractId: String? = null,
) {
    val sectorInfo get() = Campaign.SECTORS[sector.coerceIn(Campaign.SECTORS.indices)]

    /** What the goal means, for the briefing ("Find 5 beacons"). */
    val goalText: String get() = when (mode) {
        Mode.SURVIVAL -> "Survive for ${goal}s"
        Mode.BOSS_RUSH -> "Defeat $goal bosses in a row"
        Mode.BLITZ -> "Score $goal in ${seconds.toInt()}s"
        Mode.EXPLORATION -> "Find $goal beacons"
        Mode.BOUNTY -> "Destroy $goal marked targets"
        Mode.ESCORT -> "Escort the convoy to the jump gate"
        Mode.SALVAGE -> "Recover $goal salvage pods"
        Mode.MINING -> "Haul $goal ore"
        Mode.DEFENSE -> "Defend the outpost for ${goal}s"
        Mode.ACE -> "Shoot down $goal aces"
    }

    companion object {
        /** A mission of [mode] at difficulty [d], in [sector]. */
        fun of(mode: Mode, sector: Int, d: Float, seed: Int, title: String = mode.title, storyId: String? = null, contractId: String? = null): MissionSpec {
            val (goal, secs) = when (mode) {
                Mode.SURVIVAL -> (60 + (d * 10).toInt()).coerceAtMost(240) to 0f
                Mode.BOSS_RUSH -> (2 + (d / 3).toInt()).coerceAtMost(6) to 0f
                Mode.BLITZ -> (6000 + (d * 2600).toInt()) to 75f
                Mode.EXPLORATION -> (4 + (d / 2).toInt()).coerceAtMost(10) to 0f
                Mode.BOUNTY -> (3 + (d / 3).toInt()).coerceAtMost(8) to 0f
                Mode.ESCORT -> 1 to 0f
                Mode.SALVAGE -> (6 + d.toInt()).coerceAtMost(16) to 0f
                Mode.MINING -> (20 + (d * 4).toInt()).coerceAtMost(70) to 0f
                Mode.DEFENSE -> (70 + (d * 8).toInt()).coerceAtMost(200) to 0f
                Mode.ACE -> (1 + (d / 3).toInt()).coerceAtMost(5) to 0f
            }
            return MissionSpec(mode, sector, d, goal, secs, seed, title, storyId, contractId)
        }

        /** The quick-play mission a mode station offers: the hardest sector open, at its starting difficulty. */
        fun quick(mode: Mode, sector: Int, seed: Int) = of(mode, sector, Stage(sector, 2).difficulty, seed)

        /** What a cleared mission pays (before any story or contract bonus). */
        fun reward(spec: MissionSpec, cleared: Boolean): Reward {
            val d = spec.difficulty
            if (!cleared) return Reward(0, 20 + (d * 6).toInt(), 0)
            return Reward(140 + (d * 70).toInt(), Campaign.xpFor(d) + 40, if (d >= 4f) 1 else 0, 1 + (d / 2.5f).toInt())
        }
    }
}

/** One chapter of a mode's story line. Clearing it the first time pays [reward] and hands over [unlock] (a part or look key). */
data class Chapter(val mode: Mode, val index: Int, val title: String, val text: String, val difficulty: Float, val unlock: String?, val reward: Reward) {
    val id get() = "${mode.name}:$index"
    fun spec() = MissionSpec.of(mode, sectorFor(difficulty), difficulty, id.hashCode(), title, storyId = id)

    companion object {
        /** The campaign sector whose look and enemies fit a difficulty. */
        fun sectorFor(d: Float): Int = Campaign.allStages.lastOrNull { it.index == 0 && it.difficulty <= d }?.sector ?: 0
    }
}

/**
 * Each mode has a five-chapter story. Chapter 3 gives a look you can't buy, chapter 5 a part you can't buy; finishing
 * every line gives the title "The Last Light".
 */
object Stories {
    const val CHAPTERS = 5
    const val LAST_LIGHT = "title:the_last_light"

    private class Line(val mode: Mode, val names: List<Pair<String, String>>, val start: Float, val look: String, val part: String)

    private val LINES = listOf(
        Line(
            Mode.SURVIVAL, listOf(
                "First Night" to "Rim outposts are going dark. Hold the line until dawn.",
                "The Swarm" to "They come in thousands now. Don't let them close.",
                "No Retreat" to "The jump drive's dead. Survive or don't.",
                "Ember Tide" to "Something's herding them toward you.",
                "Phoenix" to "Burn bright. Rise again. Whatever comes, outlast it.",
            ), 1.5f, "color:venom", "wings:PHOENIX",
        ),
        Line(
            Mode.BOSS_RUSH, listOf(
                "Trophy Hunter" to "The Guild wants proof you can take the big ones.",
                "Back to Back" to "Two flagships, no rest between.",
                "Gauntlet" to "The arena masters have stacked the deck.",
                "Hall of Giants" to "Every legend in the sector, one after another.",
                "Eclipse" to "Their cores, fused into one hull. Take it.",
            ), 3f, "decal:RUNES", "hull:ECLIPSE",
        ),
        Line(
            Mode.BLITZ, listOf(
                "Speed Trial" to "The racing circuit wants a demo run.",
                "Hot Streak" to "Keep the chain alive, keep the score climbing.",
                "Overdrive" to "Less time, more targets.",
                "Redline" to "The engines scream. So does the crowd.",
                "Zero Point" to "Break the record and the drive is yours.",
            ), 2f, "trail:voidwake", "engine:ZERO_POINT",
        ),
        Line(
            Mode.EXPLORATION, listOf(
                "Uncharted" to "Old survey beacons are still pinging out there.",
                "Echoes" to "The signals repeat a pattern. Someone left a trail.",
                "Lost Colony" to "A colony ship vanished here. Find where it went.",
                "Mirror Field" to "The beacons are lying. Trust your eyes.",
                "Mirage" to "At the end of the trail, wings made of light.",
            ), 1f, "finish:PRISM", "wings:MIRAGE",
        ),
        Line(
            Mode.BOUNTY, listOf(
                "Wanted" to "Petty raiders with prices on their heads.",
                "Gang War" to "The Crimson Syndicate is hiring guns. Shoot them first.",
                "Blood Money" to "Somebody put a bounty on you, too.",
                "The Untouchables" to "Armoured flagships, all marked.",
                "Gauss" to "The Syndicate boss carries a rail gun. Take it from him.",
            ), 2f, "burst:star", "primary:GAUSS",
        ),
        Line(
            Mode.ESCORT, listOf(
                "Milk Run" to "One freighter, one jump gate. Easy money.",
                "Ambush Alley" to "Pirates know the route. Stay close.",
                "Refugees" to "They have nowhere else to go.",
                "Iron Corridor" to "The Choir wants this cargo badly.",
                "Paladin" to "The convoy's guardian ship, yours if they make it.",
            ), 3f, "color:royal", "hull:PALADIN",
        ),
        Line(
            Mode.SALVAGE, listOf(
                "Scrap Run" to "The Dead Fleet has pods worth a fortune.",
                "Grave Robbers" to "Rival salvagers got here first. Persuade them.",
                "Black Box" to "One of these pods holds a fleet's last orders.",
                "Live Wire" to "The wrecks are still armed.",
                "Guardian" to "Ancient drones, still loyal. Bring them home.",
            ), 2.5f, "decal:STARS", "support:GUARDIAN",
        ),
        Line(
            Mode.MINING, listOf(
                "Prospector" to "Crack rocks, scoop ore, get paid.",
                "Claim Jumpers" to "Pirates want your haul.",
                "Deep Vein" to "The rich seams are deep in the field.",
                "Core Sample" to "Something under the ice is ticking.",
                "Armageddon" to "The ore was ammunition all along. Fill the racks.",
            ), 2.5f, "trail:phoenix", "special:ARMAGEDDON",
        ),
        Line(
            Mode.DEFENSE, listOf(
                "Outpost" to "A listening post needs a guard for the night.",
                "Siege" to "They brought heavy guns this time.",
                "Last Stand" to "Reinforcements are hours away.",
                "Meteor Rain" to "The sky itself is falling on the station.",
                "Starfall" to "The outpost's flak battery, yours to keep.",
            ), 3.5f, "finish:FROST", "primary:STARFALL",
        ),
        Line(
            Mode.ACE, listOf(
                "Challenger" to "A hotshot pilot wants a duel.",
                "Wingmen" to "This time they brought friends.",
                "Red Baron" to "The Belt's top ace. Nobody's beaten him.",
                "Phantom Squadron" to "Invisible until they're on your tail.",
                "Ghost" to "Beat the last ace and claim the ship nobody can hit.",
            ), 4f, "burst:implode", "hull:GHOST",
        ),
    )

    val all: Map<Mode, List<Chapter>> = LINES.associate { l ->
        l.mode to l.names.mapIndexed { i, (t, x) ->
            val d = l.start + i * 1.6f
            val unlock = when (i) { 2 -> l.look; CHAPTERS - 1 -> l.part; else -> null }
            Chapter(l.mode, i, t, x, d, unlock, Reward(300 + i * 250 + (d * 60).toInt(), Campaign.xpFor(d) * 2, 2 + i * 2, 3 + i * 3))
        }
    }

    fun chapter(id: String): Chapter? {
        val m = runCatching { Mode.valueOf(id.substringBefore(':')) }.getOrNull() ?: return null
        return all[m]?.getOrNull(id.substringAfter(':').toIntOrNull() ?: return null)
    }
}

/** A daily job posted at the contracts board: one mission plus a bonus on top of the normal pay. */
data class Contract(val spec: MissionSpec, val credits: Int, val shards: Int, val xp: Int) {
    val id get() = spec.contractId!!
}

object Contracts {
    private val NAMES = mapOf(
        Mode.SURVIVAL to listOf("Hold the Relay", "Night Watch", "Swarm Break"),
        Mode.BOSS_RUSH to listOf("Big Game", "Flagship Cull", "Arena Bout"),
        Mode.BLITZ to listOf("Score Attack", "Time Trial", "Showboat"),
        Mode.EXPLORATION to listOf("Survey Run", "Signal Trace", "Star Charting"),
        Mode.BOUNTY to listOf("Most Wanted", "Pirate Purge", "Clean Sweep"),
        Mode.ESCORT to listOf("Cargo Run", "Medical Convoy", "VIP Transit"),
        Mode.SALVAGE to listOf("Wreck Dive", "Pod Recovery", "Scrap Haul"),
        Mode.MINING to listOf("Ore Quota", "Rich Strike", "Rock Breaker"),
        Mode.DEFENSE to listOf("Hold Position", "Station Guard", "Siege Breaker"),
        Mode.ACE to listOf("Duel Request", "Ace Hunt", "Top Gun"),
    )

    /** Three contracts in every open sector, the same all day; [open] says which modes the pilot has. */
    fun today(day: LocalDate, sectors: Int, open: List<Mode>): List<Contract> {
        if (open.isEmpty()) return emptyList()
        val out = ArrayList<Contract>()
        for (s in 0 until sectors) {
            val rnd = Random(day.toEpochDay() * 131 + s * 7919)
            val modes = open.shuffled(rnd).take(3)
            for ((i, m) in modes.withIndex()) {
                val d = Stage(s, 1 + i * 2).difficulty + rnd.nextFloat() * 0.4f
                val id = "${day}#$s#$i"
                val spec = MissionSpec.of(m, s, d, rnd.nextInt(), "${NAMES[m]!![rnd.nextInt(3)]} · ${Campaign.SECTORS[s].title}", contractId = id)
                out += Contract(spec, 120 + (d * 55).toInt() + rnd.nextInt(6) * 10, 2 + (d / 2f).toInt() + i, 40 + (d * 25).toInt())
            }
        }
        return out
    }
}

/** What the Black Market sells for void shards. */
data class Deal(val id: String, val title: String, val desc: String, val icon: String, val shards: Int, val key: String? = null, val level: Int = 1)

object Market {
    /** Boosters last this many runs. */
    const val BOOST_RUNS = 3

    val STAPLES = listOf(
        Deal("cores", "Core cache", "5 cores, no questions asked", "cores", 15),
        Deal("credits", "Laundered credits", "5,000 credits, freshly cleaned", "credits", 10),
        Deal("xp2", "Neural stim", "Double XP for $BOOST_RUNS runs", "level", 20),
        Deal("cr2", "Forged manifests", "Double credits for $BOOST_RUNS runs", "credits", 20),
        Deal("shield", "Smuggled plating", "Start your next $BOOST_RUNS runs with a shield", "shield", 12),
        Deal("bigcores", "Core crate", "25 cores, slightly radioactive", "cores", 65, level = 10),
    )

    /**
     * Today's fence deals: four shard-priced items from the hangar's catalogue, a third off. [keys] lists the catalogue
     * (key to shard price and level); owned ones are left out by the caller.
     */
    fun fence(day: LocalDate, catalogue: List<Triple<String, String, Price>>): List<Deal> {
        val rnd = Random(day.toEpochDay() * 31 + 5)
        return catalogue.filter { it.third.currency == Currency.SHARDS }.shuffled(rnd).take(4).map { (key, title, p) ->
            Deal("fence:$key", title, "A third off, today only", "supply", max(1, p.amount * 2 / 3), key, p.level)
        }
    }
}
