package com.streakapp.data.repository

import androidx.lifecycle.LiveData
import com.streakapp.data.db.HabitDao
import com.streakapp.data.model.Habit
import com.streakapp.data.model.HabitCompletion
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HabitRepository(private val dao: HabitDao) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    val allHabits: LiveData<List<Habit>> = dao.getAllHabits()

    suspend fun getAllHabitsOnce(): List<Habit> = dao.getAllHabitsOnce()

    suspend fun getHabitById(id: Long): Habit? = dao.getHabitById(id)

    suspend fun addHabit(name: String, emoji: String, notifHour: Int, notifMinute: Int): Long {
        val habit = Habit(
            name = name,
            emoji = emoji,
            notificationHour = notifHour,
            notificationMinute = notifMinute
        )
        return dao.insertHabit(habit)
    }

    suspend fun deleteHabit(habit: Habit) = dao.deleteHabit(habit)

    fun getCompletionsForHabit(habitId: Long): LiveData<List<HabitCompletion>> =
        dao.getCompletionsForHabit(habitId)

    suspend fun getLast90Completions(habitId: Long): List<HabitCompletion> =
        dao.getLast90Completions(habitId)

    /**
     * Toggle today's completion. Returns true if now completed, false if uncompleted.
     */
    suspend fun toggleTodayCompletion(habit: Habit, isVacationMode: Boolean = false): Boolean {
        val today = LocalDate.now().format(dateFormatter)
        val existing = dao.getCompletionForDate(habit.id, today)

        return if (existing == null) {
            // Mark as done
            dao.insertCompletion(HabitCompletion(habitId = habit.id, completedDate = today))
            recalculateStreak(habit, isVacationMode)
            true
        } else {
            // Undo
            dao.deleteCompletionForDate(habit.id, today)
            recalculateStreak(habit, isVacationMode)
            false
        }
    }

    suspend fun isCompletedToday(habitId: Long): Boolean {
        val today = LocalDate.now().format(dateFormatter)
        return dao.getCompletionForDate(habitId, today) != null
    }

    private suspend fun recalculateStreak(habit: Habit, isVacationMode: Boolean) {
        val completions = dao.getLast90Completions(habit.id)
            .map { LocalDate.parse(it.completedDate, dateFormatter) }
            .toSortedSet(compareByDescending { it })

        var streak = 0
        var check = LocalDate.now()

        // If today is not completed, we start checking from yesterday
        if (!completions.contains(check)) {
            check = check.minusDays(1)
        }

        while (completions.contains(check) || isVacationMode) {
            if (completions.contains(check)) {
                streak++
            } else {
                // Vacation mode: skip missing days but stop if we hit a wall of 7 missed days (sanity check)
                // In a real app, you might want to check if vacation mode was active ON that specific date.
                // For now, if global vacation mode is ON, gaps don't break the streak.
                if (check.isBefore(LocalDate.now().minusDays(90))) break 
            }
            check = check.minusDays(1)
            
            // If we found a day that IS in completions after a gap, the loop continues.
            // If we are deep in the past and no more completions exist, we should stop.
            if (completions.isEmpty() || check.isBefore(completions.last())) {
                if (!completions.contains(check) && !isVacationMode) break
                if (check.isBefore(completions.last().minusDays(1))) break
            }
        }

        val longest = maxOf(habit.longestStreak, streak)
        val lastDate = completions.firstOrNull()?.format(dateFormatter)

        dao.updateHabit(
            habit.copy(
                currentStreak = streak,
                longestStreak = longest,
                lastCompletedDate = lastDate
            )
        )
    }

    suspend fun updateHabitNotification(habit: Habit, hour: Int, minute: Int) {
        dao.updateHabit(habit.copy(notificationHour = hour, notificationMinute = minute))
    }

    suspend fun saveResetReason(habit: Habit, reason: String) {
        dao.updateHabit(habit.copy(lastResetReason = reason, currentStreak = 0))
    }

    suspend fun recoverStreak(habit: Habit, date: String) {
        dao.insertCompletion(HabitCompletion(habitId = habit.id, completedDate = date))
        val updatedHabit = habit.copy(recoveryChancesUsed = habit.recoveryChancesUsed + 1)
        recalculateStreak(updatedHabit, false)
    }

    suspend fun checkExpiredStreaks(): List<Habit> {
        val habits = dao.getAllHabitsOnce()
        val yesterday = LocalDate.now().minusDays(1).format(dateFormatter)
        val today = LocalDate.now().format(dateFormatter)
        
        return habits.filter { habit ->
            val lastDate = habit.lastCompletedDate
            // Streak is dead if not completed today AND last completed was before yesterday
            // AND we haven't already recorded a reason for this specific reset
            lastDate != null && 
            lastDate < yesterday && 
            habit.currentStreak > 0
        }
    }

    /**
     * Calculates the best streak for each day in the current month.
     */
    suspend fun getGlobalStreakData(): Map<String, Int> {
        val habits = dao.getAllHabitsOnce()
        val allCompletions = mutableMapOf<Long, Set<LocalDate>>()
        
        habits.forEach { habit ->
            allCompletions[habit.id] = dao.getLast90Completions(habit.id)
                .map { LocalDate.parse(it.completedDate, dateFormatter) }
                .toSet()
        }

        val result = mutableMapOf<String, Int>()
        val startOfMonth = LocalDate.now().withDayOfMonth(1)
        val endOfMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())

        var current = startOfMonth
        while (!current.isAfter(endOfMonth)) {
            var maxStreakForDay = 0
            
            for (habit in habits) {
                val completions = allCompletions[habit.id] ?: emptySet()
                if (completions.contains(current)) {
                    var streak = 0
                    var check = current
                    while (completions.contains(check)) {
                        streak++
                        check = check.minusDays(1)
                    }
                    if (streak > maxStreakForDay) maxStreakForDay = streak
                }
            }
            
            if (maxStreakForDay > 0) {
                result[current.format(dateFormatter)] = maxStreakForDay
            }
            current = current.plusDays(1)
        }
        return result
    }
}
