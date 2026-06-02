package com.streakapp.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lockedinbeta.R
import com.streakapp.StreakApplication
import com.streakapp.ui.habits.MainActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HabitReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val habitId = inputData.getLong("habit_id", -1L)
        val habitName = inputData.getString("habit_name") ?: return Result.success()
        val habitEmoji = inputData.getString("habit_emoji") ?: "🔥"

        val repo = (context.applicationContext as StreakApplication).repository
        val habit = repo.getHabitById(habitId) ?: return Result.success()

        // Don't notify if already done today
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        if (habit.lastCompletedDate == today) return Result.success()

        val openIntent = PendingIntent.getActivity(
            context, habitId.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val streakMsg = when {
            habit.targetCount > 1 && habit.currentCountToday > 0 && habit.currentCountToday < habit.targetCount -> {
                val remaining = habit.targetCount - habit.currentCountToday
                "You're almost there! Only $remaining more to complete ${habit.name} today."
            }
            habit.currentStreak > 0 -> "Keep your ${habit.currentStreak}-day streak alive! 🔥"
            else -> "Start a new streak today!"
        }

        val notification = NotificationCompat.Builder(context, StreakApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_streak_notification)
            .setContentTitle("$habitEmoji $habitName")
            .setContentText(streakMsg)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(habitId.toInt(), notification)

        return Result.success()
    }
}
