package com.micasong.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * Real Cast route button (full flavor). Wraps the platform [MediaRouteButton] and lets the Cast SDK
 * manage it, so it appears only when a Chromecast is on the network and opens the device chooser on
 * tap (spec §36).
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MediaRouteButton(context).also { button ->
                runCatching { CastButtonFactory.setUpMediaRouteButton(context, button) }
            }
        },
    )
}
