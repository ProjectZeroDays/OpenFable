package com.projectzerodays.quantumcli.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.projectzerodays.quantumcli.R

object Notifications {

    private const val CHANNEL_ID = "quantum_c2"
    private const val NOTIF_ID = 1

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.server_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            ch.description = context.getString(R.string.server_channel_desc)
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(ch)
        }
    }

    fun serverRunning(context: Context, port: Int) {
        try {
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_quantum)
                .setContentTitle(context.getString(R.string.server_running))
                .setContentText(context.getString(R.string.server_running_body, port))
                .setOngoing(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted; server keeps running regardless
        }
    }

    fun serverStopped(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIF_ID)
        } catch (e: SecurityException) {
            // notifications not granted
        }
    }

    fun haveNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
}
