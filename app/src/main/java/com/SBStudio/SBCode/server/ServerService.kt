package com.SBStudio.SBCode.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Without a foreground service Android may stop the app (and the server) as soon as
 * Chrome comes to the front. The notification is what keeps it alive.
 */
class ServerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Preview server", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, ServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("SBCode preview is running")
            .setContentText("Serving your project on this phone only")
            .setOngoing(true)
            .addAction(0, "Stop", stopIntent)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServerController.stop()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        ServerController.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        ServerController.stop()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "sbcode_preview"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.SBStudio.SBCode.STOP_SERVER"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ServerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ServerService::class.java))
            ServerController.stop()
        }
    }
}

object Preview {
    /** Starts the server for [dir] and returns its port. */
    fun start(context: Context, dir: File): Int {
        val port = ServerController.start(dir)
        ServerService.start(context)
        return port
    }

    /** Opens the page in a new Chrome tab. Falls back to the default browser if Chrome isn't installed. */
    fun openInBrowser(context: Context, port: Int, path: String): Boolean {
        val base = Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:$port/$path"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("create_new_tab", true)
        return try {
            context.startActivity(Intent(base).setPackage("com.android.chrome"))
            true
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(base)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }
}
