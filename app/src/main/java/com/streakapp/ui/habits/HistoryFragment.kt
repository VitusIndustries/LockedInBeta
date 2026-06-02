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
            val month = binding.historyCalendar.getDisplayedMonth()
            val streakData = repo.getGlobalStreakData(month)
            binding.historyCalendar.setStreakData(streakData)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.historyCalendar.onMonthChanged = { _ ->
            refreshData()
        }
        
        binding.btnMonthPickerGlobal.setOnClickListener {
            showMonthPicker(it) { selectedMonth ->
                binding.historyCalendar.setMonth(selectedMonth)
            }
        }
        
        binding.btnPrevMonthGlobal.setOnClickListener {
            binding.historyCalendar.prevMonth()
        }
        
        binding.btnNextMonthGlobal.setOnClickListener {
            binding.historyCalendar.nextMonth()
        }

        binding.btnMonthPickerDetail.setOnClickListener {
            showMonthPicker(it) { selectedMonth ->
                binding.habitDetailCalendar.setMonth(selectedMonth)
            }
        }
        
        binding.btnPrevMonthDetail.setOnClickListener {
            binding.habitDetailCalendar.prevMonth()
        }
        
        binding.btnNextMonthDetail.setOnClickListener {
            binding.habitDetailCalendar.nextMonth()
        }
        
        binding.habitDetailCalendar.onMonthChanged = { _ ->
            val habits = viewModel.allHabits.value
            val currentHabitName = binding.habitSelector.text.toString()
            val habit = habits?.find { "${it.emoji} ${it.name}" == currentHabitName }
            if (habit != null) showHabitDetails(habit)
        }
        
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

    private fun showMonthPicker(view: View, onMonthSelected: (java.time.YearMonth) -> Unit) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), view)
        val currentMonth = java.time.YearMonth.now()
        
        // Show last 12 months
        for (i in 0 until 12) {
            val month = currentMonth.minusMonths(i.toLong())
            val label = "${month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US)} ${month.year}"
            popup.menu.add(0, i, i, label)
        }
        
        popup.setOnMenuItemClickListener { item ->
            val selectedMonth = currentMonth.minusMonths(item.itemId.toLong())
            onMonthSelected(selectedMonth)
            true
        }
        popup.show()
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
                binding.tvStreakEndReason.text = "Last anti-habit: $reason"
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
            val positiveInsight = repo.getPositiveInsight(habit)
            val destructiveReason = repo.getMostDestructiveAntiHabit(habit.id)
            
            val insight = when {
                // Destructive pattern takes priority
                destructiveReason != null -> "Avoid $destructiveReason as this is your most destructive anti-habit."
                // Warning patterns
                failureDay != null -> "You usually break ${habit.name} around day $failureDay. Tomorrow is day $failureDay. Push through it."
                toughestDay != null -> {
                    val dayName = toughestDay.name.lowercase().replaceFirstChar { it.uppercase() }
                    "You often struggle with ${habit.name} on $dayName. Stay focused today!"
                }
                // Positive/Motivational insights
                positiveInsight != null -> positiveInsight
                else -> "Keep going — insights appear as your data grows"
            }
            binding.tvInsightText.text = insight
            
            // Subtle pulse for the lightbulb
            binding.cardInsights.findViewById<View>(com.lockedinbeta.R.id.ivInsightIcon)?.let { icon ->
                icon.animate().scaleX(1.15f).scaleY(1.15f).setDuration(300).withEndAction {
                    icon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(400).start()
                }.start()
            }

            val reasonCounts = repo.getReasonCounts(habit.id)
            if (reasonCounts.isNotEmpty()) {
                // Populate Radar Chart
                binding.radarAntiHabits.visibility = View.VISIBLE
                binding.layoutAntiHabitEmpty.visibility = View.GONE
                
                val chartData = reasonCounts.map { it.reason to it.count }
                binding.radarAntiHabits.setData(chartData)

                val reasonSummary = reasonCounts.joinToString("\n") { "${it.reason}: ${it.count} times" }
                binding.tvStreakEndReason.text = binding.tvStreakEndReason.text.toString() + "\n\nAll Anti-Habits:\n" + reasonSummary
            } else {
                binding.radarAntiHabits.visibility = View.GONE
                binding.layoutAntiHabitEmpty.visibility = View.VISIBLE
                binding.radarAntiHabits.setData(emptyList())
            }

            val month = binding.habitDetailCalendar.getDisplayedMonth()
            val completions = repo.getCompletionsForHabitOnce(habit.id)
                .map { LocalDate.parse(it.completedDate, DateTimeFormatter.ISO_LOCAL_DATE) }
                .toSet()
            
            val streakData = mutableMapOf<String, Int>()
            val startOfMonth = month.atDay(1)
            val endOfMonth = month.atEndOfMonth()
            
            var checkDate = startOfMonth
            while (!checkDate.isAfter(endOfMonth)) {
                if (completions.contains(checkDate)) {
                    var streak = 0
                    var temp = checkDate
                    while (completions.contains(temp)) {
                        streak++
                        temp = temp.minusDays(1)
                    }
                    streakData[checkDate.format(DateTimeFormatter.ISO_LOCAL_DATE)] = streak
                }
                checkDate = checkDate.plusDays(1)
            }
            binding.habitDetailCalendar.setStreakData(streakData)

            // Calculate Completion Rate (Selected Month)
            val monthCompletions = completions.count { !it.isBefore(startOfMonth) && !it.isAfter(endOfMonth) }
            val rate = (monthCompletions.toFloat() / month.lengthOfMonth() * 100).toInt()
            binding.progressCompletion.progress = rate
            binding.tvCompletionPercent.text = "$rate%"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
