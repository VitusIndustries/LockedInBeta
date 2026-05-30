package com.streakapp.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.streakapp.data.model.Habit
import com.streakapp.data.model.HabitCompletion

@Dao
interface HabitDao {

    // --- Habits ---

    @Query("SELECT * FROM habits WHERE isArchived = 0")
    fun getAllHabits(): LiveData<List<Habit>>

    @Query("SELECT * FROM habits WHERE isArchived = 0")
    suspend fun getAllHabitsOnce(): List<Habit>

    @Query("SELECT * FROM habits WHERE isArchived = 1 ORDER BY name ASC")
    fun getArchivedHabits(): LiveData<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabitById(id: Long): Habit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: Habit): Long

    @Update
    suspend fun updateHabit(habit: Habit)

    @Update
    suspend fun updateHabits(habits: List<Habit>)

    @Delete
    suspend fun deleteHabit(habit: Habit)

    // --- Completions ---

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId ORDER BY completedDate DESC")
    fun getCompletionsForHabit(habitId: Long): LiveData<List<HabitCompletion>>

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId ORDER BY completedDate DESC")
    suspend fun getCompletionsForHabitOnce(habitId: Long): List<HabitCompletion>

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId ORDER BY completedDate DESC LIMIT 90")
    suspend fun getLast90Completions(habitId: Long): List<HabitCompletion>

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND completedDate = :date LIMIT 1")
    suspend fun getCompletionForDate(habitId: Long, date: String): HabitCompletion?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(completion: HabitCompletion)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND completedDate = :date")
    suspend fun deleteCompletionForDate(habitId: Long, date: String)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId")
    suspend fun deleteCompletionsForHabit(habitId: Long)
}
