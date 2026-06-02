package com.streakapp.notifications

import android.content.Context
import androidx.work.*
import com.streakapp.data.model.Habit
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    fun scheduleNotification(context: Context, habit: Habit) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, habit.notificationHour)
            set(Calendar.MINUTE, habit.notificationMinute)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val delay = target.timeInMillis - now.timeInMillis

        val data = workDataOf(
            "habit_id" to habit.id,
            "habit_name" to habit.name,
            "habit_emoji" to habit.emoji
        )

        val request = PeriodicWorkRequestBuilder<HabitReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("habit_${habit.id}")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "habit_notif_${habit.id}",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelNotification(context: Context, habitId: Long) {
        WorkManager.getInstance(context)
            .cancelUniqueWork("habit_notif_$habitId")
    }

    fun scheduleProactiveInsights(context: Context) {
        val now = Calendar.getInstance()
        
        // 1. Proactive Insights (Daily at 8AM)
        val targetInsights = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        
        val insightsRequest = PeriodicWorkRequestBuilder<ProactiveInsightWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(targetInsights.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "proactive_insights",
            ExistingPeriodicWorkPolicy.KEEP,
            insightsRequest
        )

        // 2. Engagement Monitoring (Every 4 hours)
        val engagementRequest = PeriodicWorkRequestBuilder<EngagementNotificationWorker>(4, TimeUnit.HOURS)
            .build()
            
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "engagement_monitoring",
            ExistingPeriodicWorkPolicy.KEEP,
            engagementRequest
        )
    }
}
