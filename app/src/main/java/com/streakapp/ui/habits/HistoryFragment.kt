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

import java.util.Locale

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
    }

    private fun showHabitDetails(habit: Habit) {
        binding.individualStatsContainer.visibility = View.VISIBLE
        binding.tvHabitDetailTitle.text = habit.name
        val streakText = if (habit.longestStreak == 1) "1 day" else "${habit.longestStreak} days"
        binding.tvMaxStreakRecord.text = "Highest Streak: $streakText"
        
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
            binding.cardInsights.visibility = View.VISIBLE
            val failureDay = repo.getFailurePattern(habit.id)
            val toughestDay = repo.getToughestDayOfWeek(habit.id)
            
            val insight = when {
                failureDay != null -> "You usually break ${habit.name} around day $failureDay. Tomorrow is day $failureDay. Push through it."
                toughestDay != null -> {
                    val dayName = toughestDay.name.lowercase().replaceFirstChar { it.uppercase() }
                    "You often struggle with ${habit.name} on $dayName. Stay focused today!"
                }
                else -> "Keep going — insights appear as your data grows"
            }
            binding.tvInsightText.text = insight

            val reasonCounts = repo.getReasonCounts(habit.id)
            if (reasonCounts.isNotEmpty()) {
                val reasonSummary = reasonCounts.joinToString("\n") { "${it.reason}: ${it.count} times" }
                binding.tvStreakEndReason.text = binding.tvStreakEndReason.text.toString() + "\n\nAll Reset Reasons:\n" + reasonSummary
            }

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

            // Calculate Completion Rate (30d)
            val thirtyDaysAgo = LocalDate.now().minusDays(30)
            val recentCompletions = completions.count { !it.isBefore(thirtyDaysAgo) }
            val rate = (recentCompletions / 30f * 100).toInt()
            binding.progressCompletion.progress = rate
            binding.tvCompletionPercent.text = "$rate%"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
