package com.streakapp.data.repository

import androidx.lifecycle.LiveData
import com.streakapp.data.db.HabitDao
import com.streakapp.data.db.HabitResetDao
import com.streakapp.data.model.Habit
import com.streakapp.data.model.HabitCompletion
import com.streakapp.data.model.HabitReset
import com.streakapp.DevModeManager
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HabitRepository(
    private val dao: HabitDao,
    private val resetDao: HabitResetDao
) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    val allHabits: LiveData<List<Habit>> = dao.getAllHabits()

    val archivedHabits: LiveData<List<Habit>> = dao.getArchivedHabits()

    suspend fun getAllHabitsOnce(): List<Habit> = dao.getAllHabitsOnce()

    suspend fun getHabitById(id: Long): Habit? = dao.getHabitById(id)

    suspend fun addHabit(name: String, emoji: String, notifHour: Int, notifMinute: Int, priority: Int = 1, targetCount: Int = 1): Long {
        val habits = dao.getAllHabitsOnce()
        val nextOrder = (habits.maxOfOrNull { it.manualOrder } ?: -1) + 1
        val habit = Habit(
            name = name,
            emoji = emoji,
            notificationHour = notifHour,
            notificationMinute = notifMinute,
            priority = priority,
            manualOrder = nextOrder,
            targetCount = targetCount
        )
        return dao.insertHabit(habit)
    }

    suspend fun archiveHabit(habit: Habit) {
        dao.updateHabit(habit.copy(isArchived = true))
    }

    suspend fun unarchiveHabit(habit: Habit) {
        // Reset streak to zero when unarchiving
        dao.updateHabit(habit.copy(
            isArchived = false,
            currentStreak = 0,
            lastCompletedDate = null
        ))
    }

    suspend fun deleteHabit(habit: Habit) = dao.deleteHabit(habit)

    suspend fun updateHabitPriority(habit: Habit, priority: Int) {
        dao.updateHabit(habit.copy(priority = priority))
    }

    suspend fun updateHabitOrders(habits: List<Habit>) {
        val updatedHabits = habits.mapIndexed { index, habit ->
            habit.copy(manualOrder = index)
        }
        dao.updateHabits(updatedHabits)
    }

    fun getCompletionsForHabit(habitId: Long): LiveData<List<HabitCompletion>> =
        dao.getCompletionsForHabit(habitId)

    suspend fun getLast90Completions(habitId: Long): List<HabitCompletion> =
        dao.getLast90Completions(habitId)

    /**
     * Toggle today's completion. Returns true if now completed, false if uncompleted.
     */
    suspend fun toggleTodayCompletion(habit: Habit, isVacationMode: Boolean = false): Boolean {
        val todayDate = if (DevModeManager.isDevModeEnabled) DevModeManager.getDevToday() else LocalDate.now()
        val today = todayDate.format(dateFormatter)
        
        val todayCompletionsCount = dao.getCompletionsForHabitOnce(habit.id)
            .count { it.completedDate == today }
            
        return if (todayCompletionsCount < habit.targetCount) {
            // Increment
            dao.insertCompletion(HabitCompletion(habitId = habit.id, completedDate = today))
            
            val newCount = todayCompletionsCount + 1
            
            if (newCount >= habit.targetCount) {
                dao.updateHabit(habit.copy(currentCountToday = newCount, lastCompletedDate = today))
                recalculateStreak(habit, isVacationMode)
                true
            } else {
                dao.updateHabit(habit.copy(currentCountToday = newCount))
                false
            }
        } else {
            // Already fully done, reset count and remove today's completions
            dao.deleteCompletionForDate(habit.id, today)
            dao.updateHabit(habit.copy(currentCountToday = 0, lastCompletedDate = null))
            recalculateStreak(habit, isVacationMode)
            false
        }
    }

    suspend fun incrementHabitCount(habit: Habit, isVacationMode: Boolean = false): Habit {
        val today = LocalDate.now().format(dateFormatter)
        dao.insertCompletion(HabitCompletion(habitId = habit.id, completedDate = today))
        
        val todayCompletionsCount = dao.getCompletionsForHabitOnce(habit.id)
            .count { it.completedDate == today }
        
        val updatedHabit = habit.copy(currentCountToday = todayCompletionsCount)
        dao.updateHabit(updatedHabit)
        
        if (todayCompletionsCount >= habit.targetCount) {
            recalculateStreak(updatedHabit, isVacationMode)
        }
        return updatedHabit
    }

    suspend fun isCompletedToday(habitId: Long): Boolean {
        val today = LocalDate.now().format(dateFormatter)
        return dao.getCompletionForDate(habitId, today) != null
    }

    private suspend fun recalculateStreak(habit: Habit, isVacationMode: Boolean) {
        val todayDate = if (DevModeManager.isDevModeEnabled) DevModeManager.getDevToday() else LocalDate.now()
        
        val completions = dao.getCompletionsForHabitOnce(habit.id)
            .map { LocalDate.parse(it.completedDate, dateFormatter) }
            .toSortedSet(compareByDescending { it })

        var streak = 0
        var check = todayDate

        // If not done today, start checking from yesterday
        if (!completions.contains(check)) {
            val yesterday = check.minusDays(1)
            if (!completions.contains(yesterday) && !isVacationMode) {
                // Streak is broken
                streak = 0
            } else {
                check = yesterday
            }
        }

        // Count backward
        if (completions.contains(check) || isVacationMode) {
            while (completions.contains(check) || isVacationMode) {
                if (completions.contains(check)) {
                    streak++
                } else if (check.isBefore(todayDate.minusDays(90))) {
                    break // safety limit
                }
                
                check = check.minusDays(1)
                
                // If we've gone past the oldest completion and not in vacation mode, stop
                if (!isVacationMode && (completions.isEmpty() || check.isBefore(completions.last()))) {
                    break
                }
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
        // 1. Delete ALL history for this habit to force the streak to zero
        dao.deleteCompletionsForHabit(habit.id)

        val resetStreak = habit.currentStreak
        val dayOfWeek = LocalDate.now().dayOfWeek.value

        // 2. Clear current progress and reset streak to 0
        dao.updateHabit(
            habit.copy(
                lastResetReason = reason,
                currentStreak = 0,
                currentCountToday = 0,
                lastCompletedDate = null
            )
        )
        
        // 3. Log the reason
        resetDao.insertReset(
            HabitReset(
                habitId = habit.id,
                reason = reason,
                streakAtReset = resetStreak,
                dayOfWeek = dayOfWeek
            )
        )
    }

    suspend fun getFailurePattern(habitId: Long): Int? {
        val resets = resetDao.getResetsForHabit(habitId)
        if (resets.size < 2) return null
        
        // Count frequencies of streakAtReset
        val frequencies = resets.groupingBy { it.streakAtReset }.eachCount()
        val topPattern = frequencies.maxByOrNull { it.value }
        
        return if (topPattern != null && topPattern.value >= 2) topPattern.key else null
    }

    suspend fun getToughestDayOfWeek(habitId: Long): DayOfWeek? {
        val resets = resetDao.getResetsForHabit(habitId)
        if (resets.size < 2) return null
        
        val frequencies = resets.groupingBy { it.dayOfWeek }.eachCount()
        val topDay = frequencies.maxByOrNull { it.value }
        
        return if (topDay != null && topDay.value >= 2) DayOfWeek.of(topDay.key) else null
    }

    suspend fun getReasonCounts(habitId: Long) = resetDao.getReasonCountsForHabit(habitId)

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
