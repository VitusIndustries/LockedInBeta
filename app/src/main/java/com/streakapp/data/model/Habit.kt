package com.streakapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val emoji: String = "🔥",
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastCompletedDate: String? = null, // ISO date string: "2024-04-21"
    val createdAt: Long = System.currentTimeMillis(),
    val notificationHour: Int = 0,
    val notificationMinute: Int = 0,
    val recoveryChancesUsed: Int = 0,
    val lastResetReason: String? = null,
    val isArchived: Boolean = false,
    val priority: Int = 1, // 0: High, 1: Medium, 2: Low
    val manualOrder: Int = 0,
    val targetCount: Int = 1, // Number of times to do per day
    val currentCountToday: Int = 0, // Count for the current day
    val activeDays: Int = 127 // Bitmask for Mon-Sun
)
