package dev.m4s4t0.voidwars.model

import org.json.JSONObject
import kotlin.random.Random

/** The counters the game keeps (Game.stats). */
object Stat {
    const val KILLS = "kills"
    const val ROCKS = "rocks"
    const val BOSSES = "bosses"
    const val ELITES = "elites"
    const val GRAZES = "grazes"
    const val CREDITS = "creditsEarned"
    const val SPECIALS = "specials"
    const val CLEARS = "clears"
    const val PERFECT = "perfect"
    const val NO_HIT = "noHit"
    const val RUNS = "runs"
    const val DEATHS = "deaths"
    const val SECONDS = "seconds"
    const val COMBO_25 = "combo25"
    const val BEST_COMBO = "bestCombo"
    const val PICKUPS = "pickups"
    const val UPGRADES = "upgrades"
    const val PARTS = "parts"
    const val MISSIONS = "missions"
    const val STREAK = "streak"
    const val RIFT_BEST_SECONDS = "riftBestSeconds"

    /** Counters that hold a best rather than a running total. */
    val BESTS = setOf(BEST_COMBO, RIFT_BEST_SECONDS, STREAK)
}

/** A daily mission: push [stat] up by [goal] today. [base] is where the stat stood when it was handed out. */
data class Mission(val id: String, val stat: String, val goal: Long, val text: String, val icon: String, val credits: Int, val cores: Int, val base: Long, val done: Boolean = false) {
    fun progress(stats: Map<String, Long>) = ((stats[stat] ?: 0L) - base).coerceIn(0L, goal)

    fun toJson(): JSONObject = JSONObject().put("id", id).put("stat", stat).put("goal", goal).put("text", text).put("icon", icon)
        .put("credits", credits).put("cores", cores).put("base", base).put("done", done)

    companion object {
        fun fromJson(j: JSONObject) = Mission(
            j.getString("id"), j.getString("stat"), j.getLong("goal"), j.getString("text"), j.optString("icon", "target"),
            j.getInt("credits"), j.optInt("cores"), j.getLong("base"), j.optBoolean("done"),
        )
    }
}

object Missions {
    /** Paid when all three of the day's missions are done. */
    const val ALL_BONUS_CORES = 3

    private class Template(val id: String, val stat: String, val goal: (Int) -> Long, val text: (Long) -> String, val icon: String, val minLevel: Int = 1)

    private val TEMPLATES = listOf(
        Template("kills", Stat.KILLS, { 80L + it * 12 }, { "Shoot down $it enemies" }, "crosshair"),
        Template("rocks", Stat.ROCKS, { 40L + it * 6 }, { "Smash $it meteors" }, "rock"),
        Template("grazes", Stat.GRAZES, { 60L + it * 10 }, { "Graze $it bullets" }, "graze"),
        Template("credits", Stat.CREDITS, { 600L + it * 120 }, { "Earn $it credits" }, "credit"),
        Template("specials", Stat.SPECIALS, { 4L + it / 4 }, { "Use your special $it times" }, "bolt"),
        Template("clears", Stat.CLEARS, { 3L + it / 8 }, { "Clear $it stages" }, "flag"),
        Template("pickups", Stat.PICKUPS, { 8L + it / 2 }, { "Grab $it power-ups" }, "capsule"),
        Template("combo", Stat.COMBO_25, { 2L + it / 10 }, { "Reach a x25 chain $it times" }, "chain", minLevel = 3),
        Template("elites", Stat.ELITES, { 2L + it / 12 }, { "Take down $it carriers" }, "carrier", minLevel = 4),
        Template("perfect", Stat.PERFECT, { 1L + it / 15 }, { "Fly $it stages for three stars" }, "star", minLevel = 5),
        Template("bosses", Stat.BOSSES, { 1L }, { "Defeat a boss" }, "skull", minLevel = 6),
    )

    fun daily(rnd: Random, level: Int, stats: Map<String, Long>): List<Mission> =
        TEMPLATES.filter { it.minLevel <= level }.shuffled(rnd).take(3).map { t ->
            val goal = t.goal(level)
            val credits = 150 + level * 25 + rnd.nextInt(6) * 10
            val cores = if (rnd.nextFloat() < 0.3f) 1 else 0
            Mission(t.id, t.stat, goal, t.text(goal), t.icon, credits, cores, stats[t.stat] ?: 0L)
        }
}

/** A medal: reach [goal] on [stat] once, for [cores]. */
data class Medal(val id: String, val title: String, val desc: String, val icon: String, val stat: String, val goal: Long, val cores: Int)

val MEDALS = listOf(
    Medal("first_blood", "First blood", "Shoot down your first enemy", "crosshair", Stat.KILLS, 1, 1),
    Medal("kills_500", "Ace", "Shoot down 500 enemies", "crosshair", Stat.KILLS, 500, 3),
    Medal("kills_5000", "Squadron killer", "Shoot down 5,000 enemies", "crosshair", Stat.KILLS, 5000, 8),
    Medal("kills_25000", "Legend of the void", "Shoot down 25,000 enemies", "crosshair", Stat.KILLS, 25000, 20),
    Medal("rocks_250", "Rock breaker", "Smash 250 meteors", "rock", Stat.ROCKS, 250, 2),
    Medal("rocks_3000", "Miner", "Smash 3,000 meteors", "rock", Stat.ROCKS, 3000, 6),
    Medal("graze_500", "Close shave", "Graze 500 bullets", "graze", Stat.GRAZES, 500, 3),
    Medal("graze_5000", "Bullet dancer", "Graze 5,000 bullets", "graze", Stat.GRAZES, 5000, 8),
    Medal("graze_25000", "Untouchable", "Graze 25,000 bullets", "graze", Stat.GRAZES, 25000, 15),
    Medal("boss_1", "Giant slayer", "Defeat a boss", "skull", Stat.BOSSES, 1, 3),
    Medal("boss_10", "Boss hunter", "Defeat 10 bosses", "skull", Stat.BOSSES, 10, 10),
    Medal("elite_10", "Carrier killer", "Take down 10 carriers", "carrier", Stat.ELITES, 10, 4),
    Medal("clear_10", "On the map", "Clear 10 stages", "flag", Stat.CLEARS, 10, 3),
    Medal("clear_50", "Veteran", "Clear 50 stages", "flag", Stat.CLEARS, 50, 8),
    Medal("perfect_5", "Flawless", "Fly 5 stages for three stars", "star", Stat.PERFECT, 5, 4),
    Medal("perfect_30", "Perfectionist", "Fly all 30 stages for three stars", "star", Stat.PERFECT, 30, 20),
    Medal("nohit_1", "Ghost", "Clear a stage without being hit", "ghost", Stat.NO_HIT, 1, 4),
    Medal("nohit_10", "Phantom pilot", "Clear 10 stages without being hit", "ghost", Stat.NO_HIT, 10, 12),
    Medal("combo_50", "Chain reaction", "Reach a x50 chain", "chain", Stat.BEST_COMBO, 50, 3),
    Medal("combo_200", "Unbroken", "Reach a x200 chain", "chain", Stat.BEST_COMBO, 200, 10),
    Medal("special_50", "Big red button", "Use your special 50 times", "bolt", Stat.SPECIALS, 50, 3),
    Medal("credits_50k", "Rich pilot", "Earn 50,000 credits", "credit", Stat.CREDITS, 50000, 6),
    Medal("upgrades_20", "Tinkerer", "Buy 20 upgrades", "wrench", Stat.UPGRADES, 20, 5),
    Medal("parts_15", "Collector", "Own 15 parts", "hangar", Stat.PARTS, 15, 5),
    Medal("rift_180", "Into the Rift", "Survive 3 minutes in the Rift", "rift", Stat.RIFT_BEST_SECONDS, 180, 5),
    Medal("rift_600", "Rift walker", "Survive 10 minutes in the Rift", "rift", Stat.RIFT_BEST_SECONDS, 600, 15),
    Medal("missions_25", "Dutiful", "Finish 25 daily missions", "target", Stat.MISSIONS, 25, 6),
    Medal("streak_7", "Regular", "Fly 7 days in a row", "calendar", Stat.STREAK, 7, 5),
)
