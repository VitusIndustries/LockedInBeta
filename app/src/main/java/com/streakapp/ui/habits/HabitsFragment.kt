package com.streakapp.ui.habits

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.lockedinbeta.databinding.FragmentHabitsBinding
import com.streakapp.VibrationManager
import com.streakapp.notifications.NotificationScheduler
import kotlinx.coroutines.launch
import java.util.Collections

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
            VibrationManager.vibrateMedium(requireContext())
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
            onFailClick = { habit ->
                com.streakapp.DevModeManager.clearOverride(habit.id)
                ResetReasonBottomSheet.newInstance(habit.id)
                    .show(parentFragmentManager, "ResetReason")
            },
            onEditClick = { habit ->
                if (parentFragmentManager.findFragmentByTag("EditHabit") == null) {
                    AddHabitBottomSheet.newInstance(habit.id)
                        .show(parentFragmentManager, "EditHabit")
                }
            },
            onStreakUpdate = { _, streak ->
                (requireActivity() as? MainActivity)?.handleStreakCelebration("$streak days done! 🔥")
            },
        )
        binding.recyclerHabits.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHabits.adapter = adapter

        binding.recyclerHabits.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var hasSplashed = false
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!recyclerView.canScrollVertically(1) && dy > 0 && !hasSplashed) {
                    hasSplashed = true
                    showSubtleBottomSplash()
                    recyclerView.postDelayed({ hasSplashed = false }, 10000)
                }
            }
        })

        // Drag to Reorder & Swipe to Archive
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 
            ItemTouchHelper.RIGHT
        ) {
            private val paint = Paint().apply {
                color = Color.WHITE
                textSize = 40f
                isFakeBoldText = true
                textAlign = Paint.Align.LEFT
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                val list = adapter.currentList.toMutableList()
                Collections.swap(list, fromPos, toPos)
                adapter.submitList(list)
                viewModel.updateHabitOrder(list)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if (direction == ItemTouchHelper.RIGHT) {
                    val habit = adapter.currentList[viewHolder.bindingAdapterPosition]
                    viewModel.archiveHabit(habit)
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX > 0) {
                    val itemView = viewHolder.itemView
                    val text = "ARCHIVE"
                    val bounds = Rect()
                    paint.getTextBounds(text, 0, text.length, bounds)
                    
                    val x = itemView.left + 40f
                    val y = itemView.top + (itemView.height / 2f) + (bounds.height() / 2f)
                    
                    // Draw text only if we've swiped enough
                    if (dX > 100) {
                        c.drawText(text, x, y, paint)
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }).attachToRecyclerView(binding.recyclerHabits)
    }

    private fun showSubtleBottomSplash() {
        binding.bottomSplash.visibility = View.VISIBLE
        binding.bottomSplash.alpha = 0f
        binding.bottomSplash.animate()
            .alpha(0.4f)
            .setDuration(600)
            .withEndAction {
                binding.bottomSplash.animate()
                    .alpha(0f)
                    .setDuration(1200)
                    .withEndAction {
                        binding.bottomSplash.visibility = View.GONE
                    }
                    .start()
            }
            .start()
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

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
