package com.streakapp.ui.habits

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.streakapp.StreakApplication
import com.streakapp.data.model.Habit
import com.streakapp.data.repository.HabitRepository
import com.streakapp.ui.widget.HabitWidget
import com.streakapp.DevModeManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch
import java.time.LocalDate

class HabitViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HabitRepository =
        (application as StreakApplication).repository

    enum class SortOrder { USER_ORDER, TIME_REMAINING, IMPORTANCE }
    private val _sortOrder = MutableLiveData(SortOrder.USER_ORDER)
    val sortOrder: LiveData<SortOrder> = _sortOrder

    val allHabits: LiveData<List<Habit>> = _sortOrder.switchMap { order ->
        repository.allHabits.map { habits ->
            sortHabits(habits, order)
        }
    }

    private fun sortHabits(habits: List<Habit>, order: SortOrder): List<Habit> {
        return when (order) {
            SortOrder.USER_ORDER -> habits.sortedBy { it.manualOrder }
            SortOrder.TIME_REMAINING -> habits.sortedWith(compareBy<Habit> { 
                it.lastCompletedDate == LocalDate.now().toString()
            }.thenBy { it.notificationHour * 60 + it.notificationMinute })
            SortOrder.IMPORTANCE -> habits.sortedBy { it.priority }
        }
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    val archivedHabits: LiveData<List<Habit>> = repository.archivedHabits

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    fun addHabit(name: String, emoji: String, notifHour: Int, notifMinute: Int, priority: Int = 1, targetCount: Int = 1) {
        viewModelScope.launch {
            repository.addHabit(name, emoji, notifHour, notifMinute, priority, targetCount)
        }
    }

    fun updateHabitOrder(habits: List<Habit>) {
        if (_sortOrder.value != SortOrder.USER_ORDER) return
        viewModelScope.launch {
            repository.updateHabitOrders(habits)
        }
    }

    fun updateHabitPriority(habit: Habit, priority: Int) {
        viewModelScope.launch {
            repository.updateHabitPriority(habit, priority)
        }
    }

    fun archiveHabit(habit: Habit) {
        viewModelScope.launch {
            repository.archiveHabit(habit)
            _message.postValue("${habit.name} archived")
        }
    }

    fun unarchiveHabit(habit: Habit) {
        viewModelScope.launch {
            repository.unarchiveHabit(habit)
            _message.postValue("${habit.name} reinstated (streak reset)")
        }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            repository.deleteHabit(habit)
        }
    }

    fun manualFailHabit(habit: Habit) {
        viewModelScope.launch {
            // Clear any Dev Mode overrides so we see the real reset
            DevModeManager.clearOverride(habit.id)

            // Force reset the last completed date to a long time ago and set streak to 0
            // This will trigger the checkExpiredStreaks logic in the fragment
            repository.saveResetReason(habit, "Manual Dev Anti-Habit")
            _message.postValue("Dev: ${habit.name} failed manually")
        }
    }

    fun toggleCompletion(habit: Habit) {
        viewModelScope.launch {
            val prefs = getApplication<StreakApplication>().getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)
            val isVacationMode = prefs.getBoolean("vacation_mode", false)
            
            val completed = repository.toggleTodayCompletion(habit, isVacationMode)
            
            // Force widget refresh on every toggle
            HabitWidget().updateAll(getApplication<StreakApplication>())

            if (completed) {
                val streak = repository.getHabitById(habit.id)?.currentStreak ?: 0
                _message.postValue("${habit.emoji} ${habit.name} done! 🔥 $streak day streak")
            } else {
                _message.postValue("Unchecked ${habit.name}")
            }
        }
    }

    suspend fun isCompletedToday(habitId: Long): Boolean =
        repository.isCompletedToday(habitId)
}

class HabitViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HabitViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HabitViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
