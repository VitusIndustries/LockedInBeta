package com.streakapp.ui.habits

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.streakapp.StreakApplication
import com.lockedinbeta.databinding.FragmentHistoryBinding
import com.streakapp.data.model.Habit
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HabitViewModel by activityViewModels {
        HabitViewModelFactory(requireActivity().application)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // Reset brightness to system default to fix the "dimming" bug
        activity?.window?.attributes = activity?.window?.attributes?.apply {
            screenBrightness = -1f 
        }
        refreshData()
    }

    private fun refreshData() {
        val repo = (requireActivity().application as StreakApplication).repository
        lifecycleScope.launch {
            val streakData = repo.getGlobalStreakData()
            binding.historyCalendar.setStreakData(streakData)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup Habit Selector
        viewModel.allHabits.observe(viewLifecycleOwner) { habits ->
            val habitNames = habits.map { "${it.emoji} ${it.name}" }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, habitNames)
            binding.habitSelector.setAdapter(adapter)
            
            binding.habitSelector.setOnItemClickListener { _, _, position, _ ->
                val selectedHabit = habits[position]
                showHabitDetails(selectedHabit)
            }
        }

        binding.btnStreakRecovery.setOnClickListener {
            val habits = viewModel.allHabits.value ?: return@setOnClickListener
            if (habits.isNotEmpty()) {
                val firstHabit = habits.first()
                lifecycleScope.launch {
                    (requireActivity().application as StreakApplication).repository.toggleTodayCompletion(firstHabit)
                    android.widget.Toast.makeText(context, "Streak Recovered!", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showHabitDetails(habit: Habit) {
        binding.individualStatsContainer.visibility = View.VISIBLE
        binding.tvHabitDetailTitle.text = habit.name
        binding.tvMaxStreakRecord.text = "Highest Streak: ${habit.longestStreak} days"
        
        val lastCompleted = habit.lastCompletedDate
        if (lastCompleted != null) {
            val lastDate = LocalDate.parse(lastCompleted)
            if (lastDate.isBefore(LocalDate.now().minusDays(1))) {
                val reason = habit.lastResetReason ?: "Missed check-in on ${lastDate.plusDays(1)}"
                binding.tvStreakEndReason.text = "Last reset: $reason"
            } else {
                binding.tvStreakEndReason.text = "Status: Streak Active! 🔥"
            }
        } else {
            binding.tvStreakEndReason.text = "Status: No history recorded yet."
        }

        val repo = (requireActivity().application as StreakApplication).repository
        lifecycleScope.launch {
            val completions = repo.getLast90Completions(habit.id)
                .map { LocalDate.parse(it.completedDate, DateTimeFormatter.ISO_LOCAL_DATE) }
                .toSet()
            
            val streakData = mutableMapOf<String, Int>()
            completions.forEach { date ->
                var streak = 0
                var check = date
                while (completions.contains(check)) {
                    streak++
                    check = check.minusDays(1)
                }
                streakData[date.format(DateTimeFormatter.ISO_LOCAL_DATE)] = streak
            }
            binding.habitDetailCalendar.setStreakData(streakData)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
