package com.papercranelabs.gemblo

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Daily "come play" reminder: once a day around 7 PM, only if the player hasn't played that day.
 * A calm / brain-training message picked at random. Runs on WorkManager (survives reboots).
 */
object Reminders {
    private const val WORK = "daily_reminder"
    private const val CHANNEL = "daily_reminder"
    private val AT = LocalTime.of(19, 0)

    /** Calm, mind-peace and brain-training nudges (title, text). */
    private val MESSAGES = listOf(
        "Time for a calm break 🧘" to "A few quiet moves on the board. Let your mind breathe.",
        "Train your brain today 🧠" to "Five minutes of puzzles keeps your mind sharp.",
        "Clear the board, clear your head" to "A couple of lines and the day feels lighter.",
        "Find your focus 🎯" to "One block at a time. Calm mind, sharp moves.",
        "Goldie misses you! 💛" to "Come back and beat your best score.",
        "Daily brain warm-up" to "Plan ahead, fit the pieces, feel the click.",
        "Unwind for a moment 🌙" to "Soft music, satisfying clears. You've earned a break.",
        "Reset your mind" to "A short game is a great way to reset your day.",
        "Your journey continues ✨" to "The next story picture is waiting to be revealed.",
        "Sharp mind, happy mind" to "A quick puzzle a day keeps the brain fog away.",
    )

    fun schedule(ctx: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(AT)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork(WORK)

    fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Posts one reminder now (also used by the worker). */
    fun post(ctx: Context) {
        if (!canNotify(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A gentle daily nudge to play"
        })
        val (title, text) = MESSAGES.random()
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFFF2A516.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(1, n) } catch (_: SecurityException) {}
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val playedToday = prefs.lastPlayDay == LocalDate.now().toEpochDay()
        if (prefs.reminders && !playedToday) Reminders.post(applicationContext)
        return Result.success()
    }
}
