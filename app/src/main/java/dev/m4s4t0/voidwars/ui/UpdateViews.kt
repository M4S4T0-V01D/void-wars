package dev.m4s4t0.voidwars.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidwars.BuildConfig
import dev.m4s4t0.voidwars.Updater
import dev.m4s4t0.voidwars.Updater.State
import dev.m4s4t0.voidui.Card
import dev.m4s4t0.voidui.PillButton
import dev.m4s4t0.voidui.Void

/** Settings' Updates card: the version, Check now, and the update with its progress. */
@Composable
fun UpdatesCard() {
    val ctx = LocalContext.current
    val s = Updater.state
    Card {
        Text("Void Wars ${BuildConfig.VERSION_NAME}", color = Void.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        when (s) {
            State.Idle -> Text("Checks for new versions by itself twice a day.", color = Void.dim, fontSize = 13.sp)
            State.Checking -> Text("Checking…", color = Void.dim, fontSize = 13.sp)
            State.UpToDate -> Text("You have the latest version.", color = Void.ok, fontSize = 13.sp)
            is State.Available -> {
                Text("Version ${s.release.versionName} is ready", color = Void.cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (s.release.notes.isNotBlank()) Text(s.release.notes, color = Void.dim, fontSize = 13.sp)
            }
            is State.Downloading -> {
                Text("Downloading ${s.release.versionName}…", color = Void.text, fontSize = 13.sp)
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth(), color = Void.cyan, trackColor = Void.panelAlt)
            }
            is State.Installing -> Text("Installing ${s.release.versionName}… Android will ask you to confirm.", color = Void.text, fontSize = 13.sp)
            is State.Failed -> Text(s.message, color = Void.bad, fontSize = 13.sp)
        }
        val release = (s as? State.Available)?.release ?: (s as? State.Failed)?.release
        if (release != null) PillButton("Update to ${release.versionName}", true) { Updater.install(ctx, release) }
        else if (s !is State.Downloading && s !is State.Installing) PillButton("Check now", false) { Updater.check(ctx, quiet = false) }
    }
}

/** A line on the home screen when a new version is out. */
@Composable
fun UpdateBanner(onOpen: () -> Unit) {
    val s = Updater.state as? State.Available ?: return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Void.cyan.copy(alpha = 0.14f))
            .clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.SystemUpdate, null, tint = Void.cyan, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("Void Wars ${s.release.versionName} is ready", color = Void.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("Update", color = Void.cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
