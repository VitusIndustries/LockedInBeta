package com.streakapp.ui.habits

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lockedinbeta.databinding.FragmentHabitsBinding
import com.streakapp.notifications.NotificationScheduler
import kotlinx.coroutines.launch

class HabitsFragment : Fragment() {

    private var _binding: FragmentHabitsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HabitViewModel by activityViewModels {
        HabitViewModelFactory(requireActivity().application)
    }
    
    private lateinit var adapter: HabitAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHabitsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeHabits()
        checkExpiredStreaks()
        
        binding.fabAddHabit.setOnClickListener {
            AddHabitBottomSheet().show(parentFragmentManager, "AddHabit")
        }
    }

    private fun setupRecyclerView() {
        adapter = HabitAdapter(
            onCheckClick = { habit -> viewModel.toggleCompletion(habit) },
            onStatsClick = { habit ->
                StatsFragment.newInstance(habit.id)
                    .show(parentFragmentManager, "Stats")
            },
            onDeleteClick = { habit ->
                viewModel.deleteHabit(habit)
                NotificationScheduler.cancelNotification(requireContext(), habit.id)
            },
            onStreakUpdate = { _, streak ->
                (requireActivity() as? MainActivity)?.handleStreakCelebration("$streak days done! 🔥")
            },
        )
        binding.recyclerHabits.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHabits.adapter = adapter
    }

    private fun observeHabits() {
        viewModel.allHabits.observe(viewLifecycleOwner) { habits ->
            adapter.submitList(habits)
            binding.emptyState.visibility =
                if (habits.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun checkExpiredStreaks() {
        val repo = (requireActivity().application as com.streakapp.StreakApplication).repository
        lifecycleScope.launch {
            val expired = repo.checkExpiredStreaks()
            if (expired.isNotEmpty()) {
                ResetReasonBottomSheet.newInstance(expired.first().id)
                    .show(parentFragmentManager, "ResetReason")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
