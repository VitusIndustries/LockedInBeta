package com.streakapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.streakapp.data.db.StreakDatabase
import com.streakapp.data.repository.HabitRepository

class StreakApplication : Application() {

    val database by lazy { StreakDatabase.getDatabase(this) }
    val repository by lazy { HabitRepository(database.habitDao()) }

    override fun onCreate() {
        super.onCreate()
        applySettings()
        createNotificationChannel()
    }

    private fun applySettings() {
        val prefs = getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Habit Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily reminders to complete your habits"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "habit_reminders"
    }
}
