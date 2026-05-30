package com.streakapp.ui.habits

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.streakapp.data.model.Habit
import com.lockedinbeta.databinding.ItemHabitBinding
import com.streakapp.DevModeManager
import com.streakapp.VibrationManager
import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.streakapp.ui.fire.CinematicFireAnimation
import androidx.compose.ui.platform.ViewCompositionStrategy

class HabitAdapter(
    private val onCheckClick: (Habit) -> Unit,
    private val onStatsClick: (Habit) -> Unit,
    private val onDeleteClick: (Habit) -> Unit,
    private val onFailClick: (Habit) -> Unit = {},
    private val onStreakUpdate: (Long, Int) -> Unit = { _, _ -> }
) : ListAdapter<Habit, HabitAdapter.HabitViewHolder>(DiffCallback) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    inner class HabitViewHolder(private val binding: ItemHabitBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(habit: Habit) {
            val prefs = binding.root.context.getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)
            val isMinimalist = prefs.getBoolean("minimalist_mode", false)
            val isVacationMode = prefs.getBoolean("vacation_mode", false)
            val streakEmoji = if (isVacationMode) "❄️" else "🔥"
            
            val displayedStreak = DevModeManager.streakOverrides[habit.id] ?: habit.currentStreak

            binding.tvHabitEmoji.text = habit.emoji
            binding.tvHabitName.text = habit.name
            binding.tvStreak.text = "$displayedStreak $streakEmoji"
            binding.tvLongest.text = "Best: ${habit.longestStreak}"

            // Check if completed today
            val todayDate = if (DevModeManager.isDevModeEnabled) DevModeManager.getDevToday() else LocalDate.now()
            val today = todayDate.format(dateFormatter)
            val completedToday = habit.lastCompletedDate == today
            
            binding.btnCheck.visibility = View.VISIBLE
            
            if (habit.targetCount > 1) {
                if (completedToday || habit.currentCountToday >= habit.targetCount) {
                    binding.btnCheck.isChecked = true
                    binding.btnCheck.text = "✓ Done"
                } else {
                    binding.btnCheck.isChecked = false
                    binding.btnCheck.text = "Mark ${habit.currentCountToday}/${habit.targetCount}"
                }
            } else {
                binding.btnCheck.isChecked = completedToday
                binding.btnCheck.text = if (completedToday) "✓ Done" else "Mark Done"
            }

            // Set button color based on importance
            val importanceColor = when (habit.priority) {
                0 -> 0xFFFFCDD2.toInt() // High - Light Red
                2 -> 0xFFC8E6C9.toInt() // Low - Light Green
                else -> 0xFFFFE082.toInt() // Medium - Darker Amber/Yellow
            }
            val onImportanceColor = when (habit.priority) {
                0 -> 0xFFB71C1C.toInt() // Dark Red text
                2 -> 0xFF1B5E20.toInt() // Dark Green text
                else -> 0xFF6D4C41.toInt() // Dark Brown/Amber text
            }
            
            binding.btnCheck.backgroundTintList = android.content.res.ColorStateList.valueOf(importanceColor)
            binding.btnCheck.setTextColor(onImportanceColor)

            binding.btnCheck.setOnClickListener { 
                VibrationManager.vibrateMedium(binding.root.context)
                onCheckClick(habit)
            }
            binding.btnStats.setOnClickListener { 
                VibrationManager.vibrateSubtle(binding.root.context)
                onStatsClick(habit) 
            }

            // Removed long click listener as requested

            // Update fire animation
            binding.fireView.visibility = if (displayedStreak > 0 && !isMinimalist && !isVacationMode) View.VISIBLE else View.GONE
            binding.fireView.apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    CinematicFireAnimation(streakCount = displayedStreak)
                }
            }
            binding.tvLongest.visibility = if (isMinimalist) View.GONE else View.VISIBLE
            
            // Adjust margins for minimalist mode
            val topMargin = if (isMinimalist) 8 else 32
            val buttonsLayout = binding.btnCheck.parent as? LinearLayout
            (buttonsLayout?.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = (topMargin * binding.root.context.resources.displayMetrics.density).toInt()

            // Dev Mode Streak Override
            binding.tvStreak.setOnClickListener {
                if (DevModeManager.isDevModeEnabled) {
                    val current = DevModeManager.streakOverrides[habit.id] ?: habit.currentStreak
                    val newStreak = current + 1
                    DevModeManager.streakOverrides[habit.id] = newStreak
                    onStreakUpdate(habit.id, newStreak)
                    notifyItemChanged(bindingAdapterPosition)
                }
            }

            // Dev Mode Manual Fail
            binding.tvHabitName.setOnClickListener {
                if (DevModeManager.isDevModeEnabled) {
                    VibrationManager.vibrateStrong(binding.root.context)
                    onFailClick(habit)
                }
            }

            // Animate streak fire if active
            binding.tvStreak.animate().cancel()
            if (displayedStreak > 0) {
                binding.tvStreak.alpha = 1f
                binding.tvStreak.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .setDuration(800)
                    .withEndAction {
                        binding.tvStreak.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(800)
                            .start()
                    }
                    .start()
            } else {
                binding.tvStreak.alpha = 0.4f
                binding.tvStreak.scaleX = 1f
                binding.tvStreak.scaleY = 1f
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HabitViewHolder {
        val binding = ItemHabitBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HabitViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HabitViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Habit>() {
        override fun areItemsTheSame(oldItem: Habit, newItem: Habit) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Habit, newItem: Habit) = oldItem == newItem
    }
}
