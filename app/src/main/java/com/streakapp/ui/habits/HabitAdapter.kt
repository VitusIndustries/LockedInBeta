package com.streakapp.ui.habits

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.streakapp.data.model.Habit
import com.lockedinbeta.databinding.ItemHabitBinding
import com.streakapp.DevModeManager
import com.streakapp.ui.fire.CartoonFireView
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HabitAdapter(
    private val onCheckClick: (Habit) -> Unit,
    private val onStatsClick: (Habit) -> Unit,
    private val onDeleteClick: (Habit) -> Unit,
    private val onStreakUpdate: (Long, Int) -> Unit = { _, _ -> }
) : ListAdapter<Habit, HabitAdapter.HabitViewHolder>(DiffCallback) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    inner class HabitViewHolder(private val binding: ItemHabitBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(habit: Habit) {
            val prefs = binding.root.context.getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)
            val isMinimalist = prefs.getBoolean("minimalist_mode", false)

            val displayedStreak = DevModeManager.streakOverrides[habit.id] ?: habit.currentStreak

            binding.tvHabitEmoji.text = habit.emoji
            binding.tvHabitName.text = habit.name
            binding.tvStreak.text = "$displayedStreak 🔥"
            binding.tvLongest.text = "Best: ${habit.longestStreak}"

            // Check if completed today
            val today = LocalDate.now().format(dateFormatter)
            val completedToday = habit.lastCompletedDate == today
            binding.btnCheck.isChecked = completedToday
            binding.btnCheck.text = if (completedToday) "✓ Done" else "Mark Done"

            binding.btnCheck.setOnClickListener { onCheckClick(habit) }
            binding.btnStats.setOnClickListener { onStatsClick(habit) }
            binding.btnDelete.setOnClickListener { onDeleteClick(habit) }

            // Update fire animation
            binding.fireView.setStreak(displayedStreak)
            binding.fireView.visibility = if (displayedStreak > 0 && !isMinimalist) android.view.View.VISIBLE else android.view.View.GONE
            binding.tvLongest.visibility = if (isMinimalist) android.view.View.GONE else android.view.View.VISIBLE
            
            // Adjust margins for minimalist mode
            val topMargin = if (isMinimalist) 8 else 32
            val buttonsLayout = binding.btnCheck.parent as? android.widget.LinearLayout
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
