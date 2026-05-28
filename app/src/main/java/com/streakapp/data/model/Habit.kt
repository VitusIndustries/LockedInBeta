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
    val notificationHour: Int = 20,   // 8 PM default
    val notificationMinute: Int = 0,
    val recoveryChancesUsed: Int = 0,
    val lastResetReason: String? = null
)
