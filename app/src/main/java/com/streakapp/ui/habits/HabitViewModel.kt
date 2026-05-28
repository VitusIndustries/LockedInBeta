package com.streakapp.ui.habits

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.streakapp.StreakApplication
import com.streakapp.data.model.Habit
import com.streakapp.data.repository.HabitRepository
import kotlinx.coroutines.launch

class HabitViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HabitRepository =
        (application as StreakApplication).repository

    val allHabits: LiveData<List<Habit>> = repository.allHabits

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    fun addHabit(name: String, emoji: String, notifHour: Int, notifMinute: Int) {
        viewModelScope.launch {
            repository.addHabit(name, emoji, notifHour, notifMinute)
        }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            repository.deleteHabit(habit)
        }
    }

    fun toggleCompletion(habit: Habit) {
        viewModelScope.launch {
            val prefs = getApplication<StreakApplication>().getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)
            val isVacationMode = prefs.getBoolean("vacation_mode", false)
            
            val completed = repository.toggleTodayCompletion(habit, isVacationMode)
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
