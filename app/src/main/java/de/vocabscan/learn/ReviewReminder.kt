package de.vocabscan.learn

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.vocabscan.MainActivity
import de.vocabscan.VocabScanApplication
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ReviewReminder {
    private const val PREFS = "review_reminder"
    private const val KEY_ENABLED = "enabled"
    private const val WORK_NAME = "daily_review_reminder"
    const val CHANNEL_ID = "review_reminders"

    fun isEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        val manager = WorkManager.getInstance(context.applicationContext)
        if (enabled) {
            val now = ZonedDateTime.now()
            var next = now.withHour(18).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(java.time.Duration.between(now, next))
                .build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else manager.cancelUniqueWork(WORK_NAME)
    }
}

class ReviewReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!ReviewReminder.isEnabled(applicationContext)) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val due = (applicationContext as VocabScanApplication).repository.dueCount(System.currentTimeMillis())
        if (due <= 0) return Result.success()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            ReviewReminder.CHANNEL_ID, "Lernerinnerungen", NotificationManager.IMPORTANCE_DEFAULT,
        ))
        val intent = PendingIntent.getActivity(
            applicationContext, 0, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, ReviewReminder.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Zeit zum Lernen")
            .setContentText("$due Wiederholungen warten auf dich.")
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        manager.notify(1001, notification)
        return Result.success()
    }
}
