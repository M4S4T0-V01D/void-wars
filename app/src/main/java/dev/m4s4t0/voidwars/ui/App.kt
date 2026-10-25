package dev.m4s4t0.voidwars.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.m4s4t0.voidwars.Updater
import dev.m4s4t0.voidwars.audio.Audio
import dev.m4s4t0.voidwars.audio.Haptics
import dev.m4s4t0.voidwars.audio.Snd
import dev.m4s4t0.voidwars.audio.Song
import dev.m4s4t0.voidwars.game.Hud
import dev.m4s4t0.voidwars.game.SpaceHud
import dev.m4s4t0.voidwars.game.Station
import dev.m4s4t0.voidwars.game.WorldEvent
import dev.m4s4t0.voidwars.game.WorldListener
import dev.m4s4t0.voidwars.gl.GameRenderer
import dev.m4s4t0.voidwars.gl.GameView
import dev.m4s4t0.voidwars.model.Boss
import dev.m4s4t0.voidwars.model.Campaign
import dev.m4s4t0.voidwars.model.MissionSpec
import dev.m4s4t0.voidwars.model.Mode
import dev.m4s4t0.voidwars.model.Game
import dev.m4s4t0.voidwars.model.GameEvent
import dev.m4s4t0.voidwars.model.Loadout
import dev.m4s4t0.voidwars.model.Missions
import dev.m4s4t0.voidwars.model.Outcome
import dev.m4s4t0.voidwars.model.Stage
import dev.m4s4t0.voidui.Void
import kotlinx.coroutines.delay

/** HUB is free flight round the stations; SPACE an open-space mission; the rest float over the menu battle. */
enum class Screen { HOME, HUB, SPACE, MAP, HANGAR, PLAY, MISSIONS, SETTINGS, MARKET, CONTRACTS, MODE }

/** One flight, as the HUD sees it. [stage] is null in the Rift and the mission modes; [spec] is set for a mission. */
class RunState(val stage: Stage?, val spec: MissionSpec? = null) {
    /** Flown in open space (Space) rather than on the side-scroller (World). */
    val free get() = spec?.mode?.open == true
    var hud by mutableStateOf<Hud?>(null)
    var space by mutableStateOf<SpaceHud?>(null)
    var paused by mutableStateOf(false)
    /** Shot down: waiting on revive-or-give-up. */
    var down by mutableStateOf(false)
    var canRevive by mutableStateOf(false)
    var outcome by mutableStateOf<Outcome?>(null)
    var bossWarning by mutableStateOf<Boss?>(null)
    var bossFight by mutableStateOf(false)
    var callout by mutableStateOf<Callout?>(null)
}

data class Callout(val id: Long, val text: String, val big: Boolean)

/** A celebration card: a glyph, a title, a line, and what it paid. */
data class Toast(val id: Long, val icon: String, val title: String, val sub: String, val credits: Int = 0, val cores: Int = 0)

/** Shared between the screens and the GL view. */
class AppState {
    lateinit var view: GameView
    var screen by mutableStateOf(Screen.HOME)
    var run by mutableStateOf<RunState?>(null)
    /** The ship on the hangar's turntable: what's fitted, plus whatever's being tried on. */
    var preview by mutableStateOf(Game.loadout)
    /** The stage the map has selected. */
    var mapStage by mutableStateOf<Stage?>(null)
    val toasts = mutableStateListOf<Toast>()
    /** Free flight round the hub, as its overlay sees it. */
    var hub by mutableStateOf<SpaceHud?>(null)
    var hubCallout by mutableStateOf<Callout?>(null)
    /** The station last docked at: leaving a station screen flies out from it. */
    var dockedAt by mutableStateOf<Station?>(null)
    /** Whether the screens were opened from the hub (their back button returns there) rather than the title. */
    var fromHub by mutableStateOf(false)
    /** The mode station docked at. */
    var mode by mutableStateOf(Mode.EXPLORATION)

    fun go(s: Screen) {
        if (s == screen) return
        // Back from a station goes back out into the hub.
        if (s == Screen.HOME && fromHub) { hub(); return }
        if (s == Screen.HANGAR) preview = Game.loadout
        if (screen == Screen.PLAY || screen == Screen.SPACE) { run = null; Audio.muffled = false }
        screen = s
    }

    /** The title screen. */
    fun title() {
        fromHub = false
        hub = null
        run = null
        Audio.muffled = false
        screen = Screen.HOME
    }

    /** Out into the hub, leaving from [from] (the station last docked at). */
    fun hub(from: Station? = dockedAt) {
        if (screen == Screen.PLAY || screen == Screen.SPACE) { run = null; Audio.muffled = false }
        fromHub = true
        hubCallout = null
        if (screen != Screen.HUB) Audio.play(Snd.WHOOSH, 0.7f)
        screen = Screen.HUB
        val l = Game.loadout
        val st = Game.shipStats
        val th = Game.hubTheme
        view.gl { hub(l, st, from, th) }
        // First time in at a hub (a new one, or back from the title): name the place.
        if (Game.hubAt != shownHub) { shownHub = Game.hubAt; hubCallout = Callout(System.nanoTime(), th.title, true) }
    }
    private var shownHub = -1

    /** Jump the operation to hub [i] and fly out there. */
    fun jump(i: Int) {
        if (!Game.hubOpen(i) || i == Game.hubAt) return
        Game.moveHub(i)
        Audio.play(Snd.WHOOSH, 0.9f)
        hub(null)
    }

    /** Docked at [s]: open what it does. */
    fun dock(s: Station) {
        dockedAt = s
        fromHub = true
        Haptics.buzz(Haptics.Kind.THUD, 0.5f)
        val m = s.mode
        when {
            m != null -> { mode = m; screen = Screen.MODE }
            s == Station.RIFT -> if (Game.riftOpen) launch(null) else {
                Audio.play(Snd.DENY, 0.7f)
                hubCallout = Callout(System.nanoTime(), "Beat the ${Campaign.SECTORS[0].boss.title} to open the Rift", false)
                view.gl { space.autopilot(null) }
            }
            else -> {
                if (s == Station.HANGAR) preview = Game.loadout
                screen = when (s) {
                    Station.HANGAR -> Screen.HANGAR
                    Station.CAMPAIGN -> Screen.MAP
                    Station.CONTRACTS -> Screen.CONTRACTS
                    Station.MARKET -> Screen.MARKET
                    Station.MISSIONS -> Screen.MISSIONS
                    else -> Screen.SETTINGS
                }
            }
        }
    }

    /** Takes off on a mission: open space for the open modes, the side-scroller for the rest. */
    fun launch(spec: MissionSpec) {
        if (!Game.modeOpen(spec.mode)) return
        Audio.play(Snd.WHOOSH, 0.9f)
        Haptics.buzz(Haptics.Kind.THUD, 0.6f)
        val r = RunState(null, spec)
        run = r
        Audio.muffled = false
        val l = Game.loadout
        val st = Game.shipStats
        if (r.free) {
            screen = Screen.SPACE
            view.gl { mission(l, st, spec) }
        } else {
            screen = Screen.PLAY
            view.gl { play(l, st, null, spec) }
        }
    }

    /** Takes off for [stage], or into the Rift when it's null. */
    fun launch(stage: Stage?) {
        if (stage != null && !Game.unlocked(stage)) return
        if (stage == null && !Game.riftOpen) return
        Audio.play(Snd.WHOOSH, 0.9f)
        Haptics.buzz(Haptics.Kind.THUD, 0.6f)
        run = RunState(stage)
        screen = Screen.PLAY
        Audio.muffled = false
        val l = Game.loadout
        val st = Game.shipStats
        view.gl { play(l, st, stage) }
    }

    fun pause(p: Boolean) {
        val r = run ?: return
        if (r.outcome != null || r.down) return
        r.paused = p
        Audio.muffled = p
        view.gl { paused = p }
    }

    fun revive() {
        val r = run ?: return
        if (!r.down || !r.canRevive || !Game.spendCore(1)) return
        r.down = false
        Audio.play(Snd.POWER)
        if (r.free) view.gl { space.revive() } else view.gl { world.revive() }
    }

    fun giveUp() {
        val r = run ?: return
        r.down = false
        r.paused = false
        Audio.muffled = false
        if (r.free) view.gl { paused = false; space.giveUp() } else view.gl { paused = false; world.giveUp() }
    }

    fun toast(t: Toast) { toasts += t }

    internal fun onWorld(e: WorldEvent) {
        if (e is WorldEvent.Docked) { if (screen == Screen.HUB) dock(e.station); return }
        if (screen == Screen.HUB && e is WorldEvent.Callout) { hubCallout = Callout(System.nanoTime(), e.text, e.big); return }
        val r = run ?: return
        when (e) {
            is WorldEvent.BossWarning -> { r.bossWarning = e.boss; r.bossFight = true }
            is WorldEvent.BossDown -> { r.bossFight = false; r.callout = Callout(System.nanoTime(), "${e.boss.title} destroyed!", true) }
            WorldEvent.StageClear -> r.callout = Callout(System.nanoTime(), "Stage clear!", true)
            is WorldEvent.Died -> { r.down = true; r.canRevive = e.canRevive; Audio.muffled = true }
            is WorldEvent.Finished -> {
                r.down = false
                Audio.muffled = false
                r.outcome = Game.finishRun(e.result)
                Audio.play(if (e.result.cleared) Snd.DONE else Snd.DENY, 0.9f)
            }
            is WorldEvent.Callout -> r.callout = Callout(System.nanoTime(), e.text, e.big)
            is WorldEvent.Docked -> Unit
        }
    }
}

@Composable
fun WarsAppUi() {
    val ctx = LocalContext.current
    val state = remember { AppState() }
    val screen = state.screen
    // A new surface when the quality changes (it sets the render scale and multisampling).
    val view = remember(Game.quality, Game.cap60) {
        GameView(ctx, Game.quality, Game.cap60, object : WorldListener {
            override fun hud(h: Hud) { state.run?.hud = h }
            override fun event(e: WorldEvent) = state.onWorld(e)
            override fun space(h: SpaceHud) { if (h.hub) state.hub = h else state.run?.space = h }
        }).also { state.view = it }
    }

    // Pause the GL loop and the mixer while the app is in the background; a run in progress pauses itself.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, view) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> { state.pause(true); view.onPause(); Game.saveAll() }
                Lifecycle.Event.ON_START -> Audio.start()
                Lifecycle.Event.ON_STOP -> { Audio.stop(); Game.saveAll() }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // The GL scene follows the screen: the demo battle behind the menus, the turntable in the hangar.
    LaunchedEffect(screen, state.preview, view) {
        val p = state.preview
        when (screen) {
            Screen.PLAY, Screen.SPACE, Screen.HUB -> Unit
            Screen.HANGAR -> view.gl { hangar(p) }
            else -> view.gl { if (mode != GameRenderer.Mode.MENU) menu() }
        }
    }
    LaunchedEffect(Game.showHitbox, Game.shake, view) { val h = Game.showHitbox; val s = Game.shake; view.gl { showHitbox = h; shakeOn = s } }

    // Each sector has its own track; bosses and the Rift have theirs.
    val run = state.run
    LaunchedEffect(screen, run, run?.bossFight) {
        Audio.song = when (screen) {
            Screen.PLAY, Screen.SPACE -> when {
                run?.bossFight == true -> Song.BOSS
                run?.spec != null -> Song.forSector(run.spec.sector)
                run?.stage == null -> Song.RIFT
                else -> Song.forSector(run.stage.sector)
            }
            Screen.HANGAR -> Song.HANGAR
            else -> Song.MENU
        }
    }

    // The update notification was tapped: the update is in Settings.
    LaunchedEffect(Updater.showRequested) {
        if (Updater.showRequested && state.run == null) { Updater.showRequested = false; Updater.cancelNotification(ctx); state.go(Screen.SETTINGS) }
    }

    // Celebrations.
    LaunchedEffect(Unit) {
        Game.events.collect { e ->
            Audio.play(if (e is GameEvent.LevelUp) Snd.LEVEL else Snd.BELL, 0.9f)
            Haptics.buzz(Haptics.Kind.SUCCESS)
            when (e) {
                is GameEvent.LevelUp -> state.toast(Toast(System.nanoTime(), "level", "Pilot level ${e.level}!", "Level-up bonus", credits = 100 + e.level * 20))
                is GameEvent.MedalEarned -> state.toast(Toast(System.nanoTime(), e.medal.icon, "Medal: ${e.medal.title}", e.medal.desc, cores = e.medal.cores))
                is GameEvent.MissionDone -> {
                    state.toast(Toast(System.nanoTime(), e.mission.icon, "Mission complete", e.mission.text, e.mission.credits, e.mission.cores))
                    if (e.allDone) state.toast(Toast(System.nanoTime() + 1, "trophy", "All of today's missions done!", "Bonus cores. New orders tomorrow", cores = Missions.ALL_BONUS_CORES))
                }
            }
        }
    }

    BackHandler {
        val r = state.run
        when {
            (screen == Screen.PLAY || screen == Screen.SPACE) && r != null && r.outcome == null && !r.down -> state.pause(!r.paused)
            (screen == Screen.PLAY || screen == Screen.SPACE) && r?.outcome != null -> if (r.stage != null) state.go(Screen.MAP) else state.go(Screen.HOME)
            screen == Screen.HOME -> (ctx as? android.app.Activity)?.moveTaskToBack(true)
            screen == Screen.HUB -> state.title()
            else -> state.go(Screen.HOME)
        }
    }

    Box(Modifier.fillMaxSize().background(Void.bg)) {
        // The battle is always behind everything; the menus float over it.
        key(view) { AndroidView(factory = { view }, modifier = Modifier.fillMaxSize()) }
        when (screen) {
            Screen.HOME -> HomeScreen(state)
            Screen.HUB -> HubScreen(state)
            Screen.SPACE -> SpaceScreen(state)
            Screen.MARKET -> MarketScreen(state)
            Screen.CONTRACTS -> ContractsScreen(state)
            Screen.MODE -> ModeScreen(state)
            Screen.MAP -> MapScreen(state)
            Screen.HANGAR -> HangarScreen(state)
            Screen.PLAY -> PlayScreen(state)
            Screen.MISSIONS -> MissionsScreen(state)
            Screen.SETTINGS -> SettingsScreen(state)
        }
        Toasts(state, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun Toasts(state: AppState, modifier: Modifier) {
    val t = state.toasts.firstOrNull()
    LaunchedEffect(t?.id) {
        if (t != null) {
            delay(2800)
            state.toasts.remove(t)
        }
    }
    AnimatedVisibility(
        t != null,
        modifier.safe().padding(top = 12.dp),
        enter = slideInVertically { -it } + fadeIn() + scaleIn(initialScale = 0.8f),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        val shown = t ?: return@AnimatedVisibility
        Row(
            Modifier.widthIn(max = 460.dp).clip(RoundedCornerShape(22.dp)).background(Void.panel.copy(alpha = 0.96f))
                .border(1.5.dp, Void.cyan.copy(alpha = 0.7f), RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Glyph(shown.icon, 40.dp, Modifier.bob(3.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(shown.title, color = Void.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (shown.sub.isNotEmpty()) Text(shown.sub, color = Void.dim, fontSize = 13.sp)
                if (shown.credits > 0 || shown.cores > 0) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (shown.credits > 0) Money(shown.credits.toLong(), fontSize = 14.sp)
                    if (shown.cores > 0) Money(shown.cores.toLong(), dev.m4s4t0.voidwars.model.Currency.CORES, fontSize = 14.sp)
                }
            }
        }
    }
}
