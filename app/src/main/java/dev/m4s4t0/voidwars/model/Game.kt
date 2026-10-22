package dev.m4s4t0.voidwars.model

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.random.Random

/** Something worth celebrating on screen. */
sealed interface GameEvent {
    data class LevelUp(val level: Int) : GameEvent
    data class MedalEarned(val medal: Medal) : GameEvent
    data class MissionDone(val mission: Mission, val allDone: Boolean) : GameEvent
}

/** How hard the GPU works: the render scale, anti-aliasing, how many sparks fly and how rich the nebula is. */
enum class Quality(val title: String, val scale: Float, val msaa: Boolean, val particles: Float, val nebula: Int) {
    LOW("Battery saver", 0.6f, false, 0.45f, 1),
    MEDIUM("Balanced", 0.8f, false, 0.75f, 2),
    HIGH("Beautiful", 1f, true, 1f, 3),
}

/** What a run brought home, from the engine. [stage] is null for the Rift. */
data class RunResult(
    val stage: Stage?,
    val cleared: Boolean,
    val score: Long,
    val credits: Int,
    val kills: Int,
    val rocks: Int,
    val elites: Int,
    val bosses: Int,
    val spawned: Int,
    val grazes: Int,
    val pickups: Int,
    val specials: Int,
    val hullLeft: Float,
    val hits: Int,
    val seconds: Float,
    val bestChain: Int,
    val chains25: Int,
)

/** The debrief: what the run earned on top of what was picked up. */
data class Outcome(
    val result: RunResult,
    val stars: Int,
    val reward: Reward,
    val firstClear: Boolean,
    val newBest: Boolean,
    val levelBefore: Int,
    val levelAfter: Int,
)

/**
 * Everything the pilot has: credits, cores, XP, stats, owned parts and their upgrades, the ship, campaign stars and
 * daily missions. Compose state, so the UI follows along; saved to SharedPreferences.
 */
object Game {
    private lateinit var prefs: SharedPreferences
    private val rnd = Random(System.nanoTime())

    var credits by mutableIntStateOf(0)
        private set
    var cores by mutableIntStateOf(0)
        private set
    var xp by mutableLongStateOf(0L)
        private set
    var loadout by mutableStateOf(Loadout())
        private set
    val stats = mutableStateMapOf<String, Long>()
    private val owned = mutableStateListOf<String>()
    private val upgrades = mutableStateMapOf<String, Int>()
    val stars = mutableStateMapOf<String, Int>()
    val best = mutableStateMapOf<String, Long>()
    var riftBest by mutableLongStateOf(0L)
        private set
    val medals = mutableStateListOf<String>()
    val missions = mutableStateListOf<Mission>()
    var supplyReady by mutableStateOf(false)
        private set

    var sounds by mutableStateOf(true)
    var music by mutableStateOf(true)
    var volume by mutableStateOf(0.8f)
    var musicVolume by mutableStateOf(0.6f)
    var haptics by mutableStateOf(true)
    var quality by mutableStateOf(Quality.MEDIUM)
    /** Holds the game to 60 frames a second even on faster screens, which saves a lot of battery. */
    var cap60 by mutableStateOf(true)
    /** How far the ship moves for a finger's movement. */
    var sensitivity by mutableStateOf(1.2f)
    var showHitbox by mutableStateOf(true)
    var shake by mutableStateOf(true)

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    val level get() = Levels.of(xp).first
    val shipStats get() = loadout.stats(::upgradeLevel)

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("game", Context.MODE_PRIVATE)
        load()
        visit()
        saveAll()
    }

    private fun load() {
        credits = prefs.getInt("credits", 250)
        cores = prefs.getInt("cores", 3)
        xp = prefs.getLong("xp", 0)
        riftBest = prefs.getLong("riftBest", 0)
        sounds = prefs.getBoolean("sounds", true)
        music = prefs.getBoolean("music", true)
        volume = prefs.getFloat("volume", 0.8f)
        musicVolume = prefs.getFloat("musicVolume", 0.6f)
        haptics = prefs.getBoolean("haptics", true)
        quality = runCatching { Quality.valueOf(prefs.getString("quality", null)!!) }.getOrDefault(Quality.MEDIUM)
        cap60 = prefs.getBoolean("cap60", true)
        sensitivity = prefs.getFloat("sensitivity", 1.2f)
        showHitbox = prefs.getBoolean("showHitbox", true)
        shake = prefs.getBoolean("shake", true)
        readMap("stats") { k, j -> stats[k] = j.getLong(k) }
        readMap("upgrades") { k, j -> upgrades[k] = j.getInt(k) }
        readMap("stars") { k, j -> stars[k] = j.getInt(k) }
        readMap("best") { k, j -> best[k] = j.getLong(k) }
        owned.addAll(prefs.getStringSet("owned", emptySet())!!)
        medals.addAll(prefs.getStringSet("medals", emptySet())!!)
        loadout = runCatching { loadoutFrom(JSONObject(prefs.getString("loadout", null)!!)) }.getOrDefault(Loadout())
        val ms = runCatching { JSONArray(prefs.getString("missions", "[]")) }.getOrNull()
        if (ms != null) for (i in 0 until ms.length()) runCatching { missions += Mission.fromJson(ms.getJSONObject(i)) }
    }

    private fun readMap(key: String, put: (String, JSONObject) -> Unit) {
        val j = runCatching { JSONObject(prefs.getString(key, "{}")!!) }.getOrNull() ?: return
        j.keys().forEach { runCatching { put(it, j) } }
    }

    // ---- visiting: the daily supply drop and missions ----

    private fun visit() {
        val today = LocalDate.now()
        val last = prefs.getString("lastVisit", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        when {
            last == null -> stats[Stat.STREAK] = 1
            last == today -> Unit
            last.plusDays(1) == today -> stats[Stat.STREAK] = (stats[Stat.STREAK] ?: 0) + 1
            else -> stats[Stat.STREAK] = 1
        }
        prefs.edit().putString("lastVisit", today.toString()).apply()
        supplyReady = prefs.getString("supplyDay", null) != today.toString()
        if (prefs.getString("missionDay", null) != today.toString() || missions.isEmpty()) {
            missions.clear()
            missions.addAll(Missions.daily(rnd, level, stats))
            prefs.edit().putString("missionDay", today.toString()).apply()
        }
        checkMedals()
    }

    /** The daily supply drop's contents. */
    data class Supply(val credits: Int, val cores: Int, val streak: Int)

    fun openSupply(): Supply? {
        if (!supplyReady) return null
        supplyReady = false
        prefs.edit().putString("supplyDay", LocalDate.now().toString()).apply()
        val streak = (stats[Stat.STREAK] ?: 1).toInt()
        val amount = 150 + level * 20 + 40 * streak.coerceAtMost(10) + rnd.nextInt(8) * 10
        // A core a day, and a handful on every seventh day in a row.
        val c = if (streak % 7 == 0) 5 else 1
        credits += amount
        cores += c
        stats[Stat.CREDITS] = (stats[Stat.CREDITS] ?: 0) + amount
        saveAll()
        return Supply(amount, c, streak)
    }

    // ---- owning, buying, upgrading ----

    fun owns(key: String, price: Price) = price.free || key in owned
    fun owns(p: Part) = owns(p.key, p.price)
    fun owns(s: Swatch) = owns(s.key, s.price)
    fun owns(f: Finish) = owns(f.key, f.price)
    fun owns(d: Decal) = owns(d.key, d.price)

    fun canAfford(price: Price) = level >= price.level && when (price.currency) {
        Currency.CREDITS -> credits >= price.amount
        Currency.CORES -> cores >= price.amount
    }

    /** Buys whatever [key] names for [price]. Returns false if it's out of reach. */
    fun buy(key: String, price: Price): Boolean {
        if (owns(key, price)) return true
        if (!canAfford(price)) return false
        when (price.currency) {
            Currency.CREDITS -> credits -= price.amount
            Currency.CORES -> cores -= price.amount
        }
        owned += key
        if (key.substringBefore(':').uppercase() in Slot.entries.map { it.name }) stats[Stat.PARTS] = (stats[Stat.PARTS] ?: 0) + 1
        checkMedals()
        saveAll()
        return true
    }

    fun upgradeLevel(p: Part): Int = upgrades[p.key] ?: 1

    fun upgrade(p: Part): Boolean {
        val lvl = upgradeLevel(p)
        val cost = upgradeCost(p, lvl) ?: return false
        if (!owns(p) || credits < cost) return false
        credits -= cost
        upgrades[p.key] = lvl + 1
        stats[Stat.UPGRADES] = (stats[Stat.UPGRADES] ?: 0) + 1
        checkMedals()
        saveAll()
        return true
    }

    fun equip(p: Part) {
        if (!owns(p)) return
        loadout = loadout.with(p)
        saveAll()
    }

    /** Fits [p] as the backup gun, or takes the backup off (null). */
    fun setBackup(p: Primary?) {
        if (p != null && (!owns(p) || p == loadout.primary)) return
        loadout = loadout.copy(backup = p)
        saveAll()
    }

    fun paint(p: Paint) {
        loadout = loadout.copy(paint = p)
        saveAll()
    }

    // ---- the campaign ----

    fun unlocked(s: Stage): Boolean = when {
        s.sector == 0 && s.index == 0 -> true
        s.index > 0 -> (stars[Stage(s.sector, s.index - 1).id] ?: 0) > 0
        else -> sectorOpen(s.sector)
    }

    /** A sector opens when the one before it is beaten and the pilot is senior enough. */
    fun sectorOpen(sector: Int) = sector == 0 ||
        ((stars[Stage(sector - 1, Campaign.STAGES - 1).id] ?: 0) > 0 && level >= Campaign.SECTORS[sector].gate)

    val riftOpen get() = (stars[Campaign.RIFT_UNLOCK.id] ?: 0) > 0

    /** The stage to offer next: the first one not yet cleared, or the last one. */
    fun nextStage(): Stage = Campaign.allStages.firstOrNull { unlocked(it) && (stars[it.id] ?: 0) == 0 } ?: Campaign.allStages.last { unlocked(it) }

    fun sectorStars(sector: Int) = (0 until Campaign.STAGES).sumOf { stars[Stage(sector, it).id] ?: 0 }

    /** One revive a run, for a core. */
    fun spendCore(n: Int = 1): Boolean {
        if (cores < n) return false
        cores -= n
        saveAll()
        return true
    }

    /** Banks a finished run (cleared, lost or quit) and works out its reward. */
    fun finishRun(r: RunResult): Outcome {
        val before = level
        add(Stat.RUNS)
        add(Stat.KILLS, r.kills.toLong())
        add(Stat.ROCKS, r.rocks.toLong())
        add(Stat.ELITES, r.elites.toLong())
        add(Stat.BOSSES, r.bosses.toLong())
        add(Stat.GRAZES, r.grazes.toLong())
        add(Stat.PICKUPS, r.pickups.toLong())
        add(Stat.SPECIALS, r.specials.toLong())
        add(Stat.SECONDS, r.seconds.toLong())
        add(Stat.COMBO_25, r.chains25.toLong())
        best(Stat.BEST_COMBO, r.bestChain.toLong())
        if (!r.cleared && r.stage != null) add(Stat.DEATHS)

        var stars = 0
        var firstClear = false
        var newBest = false
        val reward: Reward
        val s = r.stage
        if (s == null) {
            best(Stat.RIFT_BEST_SECONDS, r.seconds.toLong())
            newBest = r.score > riftBest
            if (newBest) riftBest = r.score
            reward = Campaign.riftReward(r.score, r.seconds)
        } else if (r.cleared) {
            val killRatio = if (r.spawned == 0) 1f else r.kills.toFloat() / r.spawned
            stars = Campaign.stars(r.hullLeft, killRatio)
            val had = this.stars[s.id] ?: 0
            firstClear = had == 0
            if (stars > had) this.stars[s.id] = stars
            newBest = r.score > (best[s.id] ?: 0L)
            if (newBest) best[s.id] = r.score
            reward = Campaign.reward(s, stars, firstClear, stars == 3 && had < 3)
            add(Stat.CLEARS)
            if (stars == 3 && had < 3) add(Stat.PERFECT)
            if (r.hits == 0) add(Stat.NO_HIT)
        } else {
            // A lost run still pays a little XP, so every attempt moves the pilot forward.
            reward = Reward(0, 15 + r.kills / 4, 0)
        }
        credits += r.credits + reward.credits
        cores += reward.cores
        add(Stat.CREDITS, (r.credits + reward.credits).toLong())
        gainXp(reward.xp + r.kills / 2)
        checkMissions()
        checkMedals()
        saveAll()
        return Outcome(r, stars, reward, firstClear, newBest, before, level)
    }

    private fun gainXp(n: Int) {
        val before = level
        xp += n
        for (l in before + 1..level) {
            _events.tryEmit(GameEvent.LevelUp(l))
            credits += 100 + l * 20
        }
    }

    // ---- stats, missions, medals ----

    fun add(stat: String, n: Long = 1) {
        if (n == 0L) return
        stats[stat] = (stats[stat] ?: 0) + n
    }

    private fun best(stat: String, v: Long) {
        if (v > (stats[stat] ?: 0)) stats[stat] = v
    }

    private fun checkMissions() {
        for (i in missions.indices) {
            val m = missions[i]
            if (m.done || m.progress(stats) < m.goal) continue
            missions[i] = m.copy(done = true)
            add(Stat.MISSIONS)
            credits += m.credits
            cores += m.cores
            val all = missions.all { it.done }
            if (all) cores += Missions.ALL_BONUS_CORES
            _events.tryEmit(GameEvent.MissionDone(m, all))
        }
    }

    private fun checkMedals() {
        for (m in MEDALS) {
            if (m.id in medals || (stats[m.stat] ?: 0) < m.goal) continue
            medals += m.id
            cores += m.cores
            _events.tryEmit(GameEvent.MedalEarned(m))
        }
    }

    // ---- saving ----

    fun saveSettings() {
        prefs.edit()
            .putBoolean("sounds", sounds).putBoolean("music", music).putFloat("volume", volume).putFloat("musicVolume", musicVolume)
            .putBoolean("haptics", haptics).putString("quality", quality.name).putBoolean("cap60", cap60)
            .putFloat("sensitivity", sensitivity).putBoolean("showHitbox", showHitbox).putBoolean("shake", shake)
            .apply()
    }

    fun saveAll() {
        saveSettings()
        prefs.edit()
            .putInt("credits", credits).putInt("cores", cores).putLong("xp", xp).putLong("riftBest", riftBest)
            .putString("stats", JSONObject(stats.toMap()).toString())
            .putString("upgrades", JSONObject(upgrades.toMap()).toString())
            .putString("stars", JSONObject(stars.toMap()).toString())
            .putString("best", JSONObject(best.toMap()).toString())
            .putStringSet("owned", owned.toSet())
            .putStringSet("medals", medals.toSet())
            .putString("loadout", loadoutJson(loadout).toString())
            .putString("missions", JSONArray(missions.map { it.toJson() }).toString())
            .apply()
    }

    /** Back to a brand-new pilot (settings stay). */
    fun reset() {
        val keep = listOf("sounds", "music", "volume", "musicVolume", "haptics", "quality", "cap60", "sensitivity", "showHitbox", "shake")
        val e = prefs.edit()
        prefs.all.keys.filter { it !in keep }.forEach { e.remove(it) }
        e.commit()
        stats.clear(); owned.clear(); upgrades.clear(); stars.clear(); best.clear(); medals.clear(); missions.clear()
        load()
        visit()
        saveAll()
    }

    fun loadoutJson(l: Loadout): JSONObject = JSONObject()
        .put("hull", l.hull.name).put("wings", l.wings.name).put("engine", l.engine.name)
        .put("primary", l.primary.name).put("support", l.support.name).put("special", l.special.name)
        .put("c1", l.paint.primary).put("c2", l.paint.secondary).put("c3", l.paint.glow)
        .put("finish", l.paint.finish.name).put("decal", l.paint.decal.name)
        .put("backup", l.backup?.name ?: "")

    fun loadoutFrom(j: JSONObject): Loadout {
        fun <T : Enum<T>> e(values: List<T>, key: String, def: T) = values.firstOrNull { it.name == j.optString(key) } ?: def
        val d = Loadout()
        val primary = e(Primary.entries, "primary", d.primary)
        return Loadout(
            e(Hull.entries, "hull", d.hull), e(Wings.entries, "wings", d.wings), e(Engine.entries, "engine", d.engine),
            primary, e(Support.entries, "support", d.support), e(Special.entries, "special", d.special),
            Paint(
                j.optString("c1", d.paint.primary), j.optString("c2", d.paint.secondary), j.optString("c3", d.paint.glow),
                e(Finish.entries, "finish", d.paint.finish), e(Decal.entries, "decal", d.paint.decal),
            ),
            Primary.entries.firstOrNull { it.name == j.optString("backup") && it != primary },
        )
    }
}
