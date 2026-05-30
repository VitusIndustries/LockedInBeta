package com.streakapp.ui.habits

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lockedinbeta.databinding.FragmentAllStatsBinding

class AllStatsFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentAllStatsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HabitViewModel by activityViewModels {
        HabitViewModelFactory(requireActivity().application)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAllStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.allHabits.observe(viewLifecycleOwner) { habits ->
            val totalHabits = habits.size
            val activeStreaks = habits.count { it.currentStreak > 0 }
            val longestOverall = habits.maxOfOrNull { it.longestStreak } ?: 0
            val avgStreak = if (habits.isNotEmpty())
                habits.sumOf { it.currentStreak } / habits.size else 0

            binding.tvTotalHabits.text = "📋 Total habits: $totalHabits"
            binding.tvActiveStreaks.text = "🔥 Active streaks: $activeStreaks"
            val longestText = if (longestOverall == 1) "1 day" else "$longestOverall days"
            val avgText = if (avgStreak == 1) "1 day" else "$avgStreak days"
            binding.tvLongestEver.text = "🏆 Longest streak ever: $longestText"
            binding.tvAvgStreak.text = "📊 Avg current streak: $avgText"

            // Build leaderboard
            val leaderboard = habits.sortedByDescending { it.currentStreak }
                .joinToString("\n") { 
                    val daysText = if (it.currentStreak == 1) "1 day" else "${it.currentStreak} days"
                    "${it.emoji} ${it.name}: $daysText" 
                }
            binding.tvLeaderboard.text = leaderboard.ifEmpty { "No habits yet!" }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
