package dev.m4s4t0.voidwars

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * In-app updates from this app's GitHub releases (a public repository, so no key is needed). ./release.sh
 * publishes VoidWars.apk with a latest.json beside it. The APK is checked against
 * the published SHA-256, then handed to Android's installer, which asks the user to confirm. Android itself refuses
 * an update signed with another key.
 */
object Updater {
    private const val API = "https://api.github.com/repos/"
    const val ACTION_SHOW = "dev.m4s4t0.voidwars.SHOW_UPDATE"
    private const val CHECK_EVERY_MS = 3 * 60 * 60 * 1000L
    private const val CHANNEL = "updates"
    private const val NOTIF_ID = 9001

    data class Release(val versionCode: Long, val versionName: String, val sha256: String, val size: Long, val notes: String, val apkUrl: String)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Installing(val release: Release) : State
        data class Failed(val message: String, val release: Release? = null) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set
    /** Set when the update notification is tapped; the app then opens Settings, where the update is. */
    var showRequested by mutableStateOf(false)
    /** Set while the app is on screen; the update notification is skipped then (the app says so instead). */
    @Volatile var foreground = false
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private fun set(s: State) = main.post { state = s }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("updates", Context.MODE_PRIVATE)

    fun installedCode(ctx: Context): Long = ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode

    /** Checks in the background. [quiet] skips it if checked recently and keeps errors silent. */
    fun check(ctx: Context, quiet: Boolean) {
        val app = ctx.applicationContext
        io.execute { checkNow(app, quiet) }
    }

    private fun open(url: String, accept: String = "application/vnd.github+json"): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        useCaches = false
        instanceFollowRedirects = true
        setRequestProperty("Accept", accept)
        setRequestProperty("User-Agent", "VoidWars/${BuildConfig.VERSION_NAME}")
    }

    private fun readJson(url: String): JSONObject? {
        val c = open(url)
        try {
            if (c.responseCode == 404) return null
            if (c.responseCode != 200) throw IllegalStateException("GitHub answered ${c.responseCode}")
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    private fun release(j: JSONObject, apkUrl: String) =
        Release(j.optLong("versionCode"), j.optString("versionName", "?"), j.optString("sha256"), j.optLong("size"), j.optString("notes"), apkUrl)

    /** The latest release's manifest and APK address, or null if nothing is published yet. */
    private fun latest(): Release? {
        val rel = readJson(API + BuildConfig.UPDATE_REPO + "/releases/latest") ?: return null
        val assets = rel.getJSONArray("assets")
        var meta: String? = null
        var apk: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            when (a.getString("name")) {
                "latest.json" -> meta = a.getString("browser_download_url")
                "VoidWars.apk" -> apk = a.getString("browser_download_url")
            }
        }
        if (meta == null || apk == null) return null
        return readJson(meta)?.let { release(it, apk) }
    }

    /** Blocking check; returns the newer release, if any. */
    fun checkNow(ctx: Context, quiet: Boolean): Release? {
        val s = state
        if (s is State.Downloading || s is State.Installing) return null
        val now = System.currentTimeMillis()
        if (quiet && now - prefs(ctx).getLong("lastCheck", 0) < CHECK_EVERY_MS) {
            // Checked a moment ago (perhaps before Android closed the app): offer what was found then.
            val known = (s as? State.Available)?.release ?: remembered(ctx)
            if (known != null && s !is State.Available) set(State.Available(known))
            return known
        }
        if (!quiet) set(State.Checking)
        return try {
            val r = latest()
            prefs(ctx).edit().putLong("lastCheck", now).apply()
            if (r != null && r.versionCode > installedCode(ctx)) {
                prefs(ctx).edit().putString("latest", JSONObject().put("versionCode", r.versionCode).put("versionName", r.versionName)
                    .put("sha256", r.sha256).put("size", r.size).put("notes", r.notes).put("apk", r.apkUrl).toString()).apply()
                set(State.Available(r))
                if (!foreground && prefs(ctx).getLong("notified", 0) < r.versionCode) {
                    prefs(ctx).edit().putLong("notified", r.versionCode).apply()
                    notify(ctx, r)
                }
                r
            } else {
                set(State.UpToDate)
                null
            }
        } catch (e: Exception) {
            if (!quiet) set(State.Failed("Couldn't check for updates (${e.message ?: e.javaClass.simpleName})"))
            null
        }
    }

    /** The newer release the last check found, if it's still newer than this app. */
    private fun remembered(ctx: Context): Release? = runCatching {
        val j = JSONObject(prefs(ctx).getString("latest", null) ?: return null)
        release(j, j.getString("apk"))
    }.getOrNull()?.takeIf { it.versionCode > installedCode(ctx) }

    /** Downloads, verifies and installs [r]. May first send the user to allow installs from this app. */
    fun install(ctx: Context, r: Release) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            set(State.Failed("Allow Void Wars to install apps, then tap Update again.", r))
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val app = ctx.applicationContext
        set(State.Downloading(r, 0f))
        io.execute {
            try {
                val file = download(app, r)
                set(State.Installing(r))
                commit(app, file)
            } catch (e: Exception) {
                set(State.Failed(e.message ?: "The update failed", r))
            }
        }
    }

    private fun download(ctx: Context, r: Release): File {
        val out = File(ctx.cacheDir, "update-${r.versionCode}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val c = open(r.apkUrl, accept = "application/octet-stream")
        try {
            if (c.responseCode != 200) throw IllegalStateException("The update server answered ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
            var done = 0L
            var shown = 0f
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        val p = if (total > 0) done.toFloat() / total else 0f
                        if (p - shown > 0.02f) {
                            shown = p
                            set(State.Downloading(r, p))
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (!sha.equals(r.sha256, ignoreCase = true)) {
            out.delete()
            throw IllegalStateException("The download was damaged; try again")
        }
        return out
    }

    private fun commit(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            // Lets Android skip the confirmation once this app is the one that installed itself.
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (Build.VERSION.SDK_INT >= 33) setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("void-wars.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            // The installer fills in the status extras, so the PendingIntent must be mutable.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            session.commit(PendingIntent.getBroadcast(ctx, id, Intent(ctx, UpdateReceiver::class.java), flags).intentSender)
        }
        apk.delete()
    }

    /** The installer's answer, forwarded by [UpdateReceiver]. */
    internal fun onInstallStatus(ctx: Context, intent: Intent) {
        val r = (state as? State.Installing)?.release
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // Android restarts the app on the new version.
            PackageInstaller.STATUS_FAILURE_ABORTED -> set(if (r != null) State.Available(r) else State.Idle)
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "The update failed"
                set(State.Failed("Not installed: $msg", r))
            }
        }
    }

    private fun notify(ctx: Context, r: Release) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 9001, Intent(ctx, MainActivity::class.java).setAction(ACTION_SHOW), PendingIntent.FLAG_IMMUTABLE)
        val text = r.notes.ifBlank { "Tap to update" }
        nm.notify(
            NOTIF_ID,
            Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Void Wars ${r.versionName} is ready")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun cancelNotification(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Updater.onInstallStatus(context, intent)
}

/** Checks for updates twice a day in the background, whenever there's a network. */
class UpdateJob : JobService() {
    companion object {
        private const val JOB_ID = 9002

        /** Background checks are optional: if the system refuses the job, the app still opens. */
        fun schedule(ctx: Context) = runCatching {
            val js = ctx.getSystemService(JobScheduler::class.java)
            if (js.getPendingJob(JOB_ID) != null) return@runCatching
            js.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(ctx, UpdateJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(12 * 60 * 60 * 1000L)
                    .setPersisted(true)
                    .build(),
            )
        }.onFailure { Log.w("VoidWars", "update job not scheduled", it) }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            Updater.checkNow(applicationContext, quiet = true)
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
