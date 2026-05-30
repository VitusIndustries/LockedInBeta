package com.streakapp.ui.habits

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.streakapp.StreakApplication
import com.lockedinbeta.databinding.FragmentStatsBinding
import com.streakapp.ui.stats.StatsViewModel
import com.streakapp.ui.stats.StatsViewModelFactory
import kotlinx.coroutines.launch

class StatsFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var statsViewModel: StatsViewModel

    companion object {
        private const val ARG_HABIT_ID = "habit_id"
        fun newInstance(habitId: Long) = StatsFragment().apply {
            arguments = Bundle().apply { putLong(ARG_HABIT_ID, habitId) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val habitId = arguments?.getLong(ARG_HABIT_ID) ?: return
        val repo = (requireActivity().application as StreakApplication).repository
        statsViewModel = viewModels<StatsViewModel> {
            StatsViewModelFactory(repo, habitId)
        }.value

        statsViewModel.habit.observe(viewLifecycleOwner) { habit ->
            habit ?: return@observe
            binding.tvStatsTitle.text = "${habit.emoji} ${habit.name}"
            val currentText = if (habit.currentStreak == 1) "1 day" else "${habit.currentStreak} days"
            val longestText = if (habit.longestStreak == 1) "1 day" else "${habit.longestStreak} days"
            binding.tvCurrentStreak.text = "🔥 Current: $currentText"
            binding.tvLongestStreak.text = "🏆 Longest: $longestText"
        }

        statsViewModel.last30Days.observe(viewLifecycleOwner) { completedDates ->
            // Use the standard orange for per-habit view or the new streak-based colors?
            // Let's calculate the streak for this specific habit to show progress colors
            lifecycleScope.launch {
                val habitId = arguments?.getLong(ARG_HABIT_ID) ?: return@launch
                val completions = repo.getLast90Completions(habitId)
                    .map { java.time.LocalDate.parse(it.completedDate, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) }
                    .toSet()
                
                val streakData = mutableMapOf<String, Int>()
                completions.forEach { date ->
                    var streak = 0
                    var check = date
                    while (completions.contains(check)) {
                        streak++
                        check = check.minusDays(1)
                    }
                    streakData[date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)] = streak
                }
                binding.calendarView.setStreakData(streakData)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
