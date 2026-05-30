package com.streakapp

object DevModeManager {
    var isDevModeEnabled = false
    val streakOverrides = mutableMapOf<Long, Int>()
    var dateOffsetDays = 0

    fun clearOverride(habitId: Long) {
        streakOverrides.remove(habitId)
    }

    fun getDevToday(): java.time.LocalDate {
        return java.time.LocalDate.now().plusDays(dateOffsetDays.toLong())
    }
}
