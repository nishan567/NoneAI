package com.nonea.ai.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val text = inputData.getString("text") ?: "Reminder"
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("nonea", "Nonea reminders", NotificationManager.IMPORTANCE_HIGH))
        val n = Notification.Builder(applicationContext, "nonea")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Nonea").setContentText(text).setAutoCancel(true).build()
        try { nm.notify(System.currentTimeMillis().toInt(), n) } catch (_: SecurityException) {}
        return Result.success()
    }
}
