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

    suspend fun addHabit(name: String, emoji: String, notifHour: Int, notifMinute: Int, priority: Int = 1, targetCount: Int = 1, activeDays: Int = 127): Long {
        val habits = dao.getAllHabitsOnce()
        val nextOrder = (habits.maxOfOrNull { it.manualOrder } ?: -1) + 1
        val habit = Habit(
            name = name,
            emoji = emoji,
            notificationHour = notifHour,
            notificationMinute = notifMinute,
            priority = priority,
            manualOrder = nextOrder,
            targetCount = targetCount,
            activeDays = activeDays
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

    suspend fun updateHabit(habit: Habit) {
        dao.updateHabit(habit)
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

    suspend fun getCompletionsForHabitOnce(habitId: Long): List<HabitCompletion> =
        dao.getCompletionsForHabitOnce(habitId)

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

    private fun isDayActive(habit: Habit, date: LocalDate): Boolean {
        val dayOfWeek = date.dayOfWeek.value // 1 (Mon) to 7 (Sun)
        val bit = 1 shl (dayOfWeek - 1)
        return (habit.activeDays and bit) != 0
    }

    private suspend fun recalculateStreak(habit: Habit, isVacationMode: Boolean) {
        val todayDate = if (DevModeManager.isDevModeEnabled) DevModeManager.getDevToday() else LocalDate.now()
        
        val completions = dao.getCompletionsForHabitOnce(habit.id)
            .map { LocalDate.parse(it.completedDate, dateFormatter) }
            .toSortedSet(compareByDescending { it })

        var streak = 0
        var check = todayDate

        // If today is NOT active and NOT completed, we skip it and look at yesterday.
        // If today is active but NOT completed, the streak might be broken.
        if (!completions.contains(check)) {
            if (!isDayActive(habit, check)) {
                check = check.minusDays(1)
            } else {
                val yesterday = check.minusDays(1)
                if (!completions.contains(yesterday) && !isVacationMode) {
                    // Check if yesterday was active. If not, look further back.
                    var tempCheck = yesterday
                    while (!isDayActive(habit, tempCheck) && tempCheck.isAfter(todayDate.minusDays(7))) {
                        tempCheck = tempCheck.minusDays(1)
                    }
                    if (!completions.contains(tempCheck)) {
                         streak = 0
                    } else {
                        check = tempCheck
                    }
                } else {
                    check = yesterday
                }
            }
        }

        // Improved count backward: skip inactive days
        if (completions.contains(check) || isVacationMode) {
            var tempCheck = check
            while (streak < 365) { // Safety limit
                if (completions.contains(tempCheck)) {
                    streak++
                } else if (isDayActive(habit, tempCheck) && !isVacationMode) {
                    break // Broken streak
                }
                
                tempCheck = tempCheck.minusDays(1)
                
                if (tempCheck.isBefore(todayDate.minusDays(180))) break
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

    suspend fun getPositiveInsight(habit: Habit): String? {
        // 1. Record Streak
        if (habit.currentStreak > 1 && habit.currentStreak >= habit.longestStreak) {
            return "You've completed this habit ${habit.currentStreak} days in a row — your best ever!"
        }

        // 2. Milestone approach
        val milestones = listOf(7, 14, 30, 60, 90, 100, 365)
        for (m in milestones) {
            if (habit.currentStreak < m && habit.currentStreak >= m - 3 && habit.currentStreak > 0) {
                val diff = m - habit.currentStreak
                return "You're $diff days from your next milestone ($m days)."
            }
        }

        // 3. Weekly completion rate
        val completions = dao.getLast90Completions(habit.id)
        val today = LocalDate.now()
        val last7DaysCount = completions.count { 
            val date = LocalDate.parse(it.completedDate, dateFormatter)
            !date.isBefore(today.minusDays(7))
        }
        if (last7DaysCount == 6 && !completions.any { it.completedDate == today.format(dateFormatter) }) {
            return "You've hit 80% of your weekly goal — one more day seals it."
        }

        // 4. Time-based pattern
        if (completions.size >= 5) {
            val weekdayCompletions = completions.filter {
                val date = LocalDate.parse(it.completedDate, dateFormatter)
                date.dayOfWeek.value in 1..5
            }
            if (weekdayCompletions.size >= 3 && !completions.any { it.completedDate == today.format(dateFormatter) }) {
                val avgHour = weekdayCompletions.map { 
                    val instant = java.time.Instant.ofEpochMilli(it.completedAt)
                    java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()).hour
                }.average()
                
                val currentHour = java.time.LocalTime.now().hour
                if (currentHour > avgHour + 2) {
                    return "You tend to complete this habit earlier on weekdays. Today's running late."
                }
            }
        }

        // 5. Resilience/Recovery
        val allCompletions = dao.getCompletionsForHabitOnce(habit.id)
            .map { LocalDate.parse(it.completedDate, dateFormatter) }
            .sorted()
        
        if (allCompletions.size >= 3) {
            var maxGap = 0L
            for (i in 0 until allCompletions.size - 1) {
                val gap = java.time.temporal.ChronoUnit.DAYS.between(allCompletions[i], allCompletions[i+1]) - 1
                if (gap > maxGap) maxGap = gap
            }
            if (maxGap in 1..3) {
                return "Your longest gap before bouncing back was $maxGap days. You're resilient."
            }
        }

        return null
    }

    suspend fun getMostDestructiveAntiHabit(habitId: Long): String? {
        val counts = resetDao.getReasonCountsForHabit(habitId)
        return counts.firstOrNull()?.reason
    }

    suspend fun getReasonCounts(habitId: Long) = resetDao.getReasonCountsForHabit(habitId)

    suspend fun getHabitsCompletedOnDate(date: String): List<Habit> {
        val completions = dao.getCompletionsByDate(date)
        val habitIds = completions.map { it.habitId }.distinct()
        val allHabits = dao.getAllHabitsOnce()
        return allHabits.filter { habitIds.contains(it.id) }
    }

    suspend fun recoverStreak(habit: Habit, date: String) {
        dao.insertCompletion(HabitCompletion(habitId = habit.id, completedDate = date))
        val updatedHabit = habit.copy(recoveryChancesUsed = habit.recoveryChancesUsed + 1)
        recalculateStreak(updatedHabit, false)
    }

    suspend fun checkExpiredStreaks(): List<Habit> {
        val habits = dao.getAllHabitsOnce()
        val today = LocalDate.now()
        
        return habits.filter { habit ->
            if (habit.currentStreak == 0) return@filter false
            
            val lastDateStr = habit.lastCompletedDate ?: return@filter false
            val lastDate = LocalDate.parse(lastDateStr, dateFormatter)
            
            // If today is active and not done, check if it's already dead
            if (isDayActive(habit, today)) {
                // If not done today, and last completion was before today
                if (lastDate.isBefore(today)) {
                    // It's dead if the previous active day was also missed
                    var prevActive = today.minusDays(1)
                    while (!isDayActive(habit, prevActive) && prevActive.isAfter(today.minusDays(7))) {
                        prevActive = prevActive.minusDays(1)
                    }
                    lastDate.isBefore(prevActive)
                } else {
                    false
                }
            } else {
                // Today is an off-day, streak is safe for now
                false
            }
        }
    }

    /**
     * Calculates the completion percentage (0-100) for each day in the provided month.
     */
    suspend fun getGlobalStreakData(month: java.time.YearMonth = java.time.YearMonth.now()): Map<String, Int> {
        val habits = dao.getAllHabitsOnce()
        if (habits.isEmpty()) return emptyMap()

        val allCompletions = mutableMapOf<Long, Set<LocalDate>>()
        habits.forEach { habit ->
            allCompletions[habit.id] = dao.getCompletionsForHabitOnce(habit.id)
                .map { LocalDate.parse(it.completedDate, dateFormatter) }
                .toSet()
        }

        val result = mutableMapOf<String, Int>()
        val startOfMonth = month.atDay(1)
        val endOfMonth = month.atEndOfMonth()

        var current = startOfMonth
        while (!current.isAfter(endOfMonth)) {
            var completedCount = 0
            
            for (habit in habits) {
                val completions = allCompletions[habit.id] ?: emptySet()
                if (completions.contains(current)) {
                    completedCount++
                }
            }
            
            if (completedCount > 0) {
                val percentage = (completedCount.toFloat() / habits.size * 100).toInt()
                result[current.format(dateFormatter)] = percentage
            }
            current = current.plusDays(1)
        }
        return result
    }
}
