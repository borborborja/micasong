package com.micasong.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.micasong.player.MainActivity
import com.micasong.player.R
import com.micasong.player.api.ApiReceiver
import com.micasong.player.playback.NowPlayingState

/**
 * Home-screen widget (spec §40). Shows the current title/artist and prev / play-pause / next
 * controls. The buttons drive playback through the existing broadcast API (`MEDIA_COMMAND`, §42),
 * so the widget needs no direct binding to the player. [updateAll] is called by the playback
 * layer whenever the now-playing state changes, keeping the widget live.
 */
class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = buildViews(context, NowPlayingState())
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {

        /** Rebuild every widget instance from the latest playback state. */
        fun updateAll(context: Context, state: NowPlayingState) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context, state)
            manager.updateAppWidget(ids, views)
        }

        private fun buildViews(context: Context, state: NowPlayingState): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
            views.setTextViewText(R.id.widget_title, state.title.ifBlank { context.getString(R.string.app_name) })
            views.setTextViewText(R.id.widget_artist, state.artist)
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            )

            views.setOnClickPendingIntent(R.id.widget_prev, command(context, "previous", 1))
            views.setOnClickPendingIntent(R.id.widget_play_pause, command(context, if (state.isPlaying) "pause" else "play", 2))
            views.setOnClickPendingIntent(R.id.widget_next, command(context, "next", 3))
            // Tapping the artwork/title opens the app.
            views.setOnClickPendingIntent(R.id.widget_icon, openApp(context))
            return views
        }

        private fun command(context: Context, command: String, requestCode: Int): PendingIntent {
            val intent = Intent(ApiReceiver.ACTION_MEDIA_COMMAND).apply {
                setPackage(context.packageName)
                setClass(context, ApiReceiver::class.java)
                putExtra(ApiReceiver.EXTRA_COMMAND, command)
            }
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun openApp(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
