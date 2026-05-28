package com.streakapp.ui.stats

import androidx.lifecycle.*
import com.streakapp.data.model.Habit
import com.streakapp.data.model.HabitCompletion
import com.streakapp.data.repository.HabitRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class StatsViewModel(
    private val repository: HabitRepository,
    private val habitId: Long
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    val habit: LiveData<Habit?> = liveData {
        emit(repository.getHabitById(habitId))
        emitSource(repository.allHabits.map { it.find { h -> h.id == habitId } })
    }

    val last30Days: LiveData<Set<String>> = liveData {
        val completions = repository.getLast90Completions(habitId)
        val dates = completions.map { it.completedDate }.toSet()
        emit(dates)
        emitSource(repository.getCompletionsForHabit(habitId).map { list ->
            val cutoff = LocalDate.now().minusDays(30).format(dateFormatter)
            list.filter { it.completedDate >= cutoff }.map { it.completedDate }.toSet()
        })
    }
}

class StatsViewModelFactory(
    private val repository: HabitRepository,
    private val habitId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StatsViewModel(repository, habitId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel")
    }
}
