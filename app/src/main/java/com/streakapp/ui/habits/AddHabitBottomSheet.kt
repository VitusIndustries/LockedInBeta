package com.streakapp.ui.habits

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.children
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.lockedinbeta.databinding.BottomSheetAddHabitBinding
import com.streakapp.VibrationManager
import com.streakapp.SoundManager
import java.util.Calendar

class AddHabitBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddHabitBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HabitViewModel by activityViewModels {
        HabitViewModelFactory(requireActivity().application)
    }

    private var notifHour = 20
    private var notifMinute = 0
    private var habitId: Long = -1L

    companion object {
        private const val ARG_HABIT_ID = "habit_id"
        fun newInstance(habitId: Long = -1L) = AddHabitBottomSheet().apply {
            arguments = Bundle().apply { putLong(ARG_HABIT_ID, habitId) }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        
        dialog.setOnShowListener {
            val d = it as BottomSheetDialog
            val bottomSheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as? android.widget.FrameLayout
            bottomSheet?.let { bs ->
                val behavior = BottomSheetBehavior.from(bs)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                
                // Force full height to avoid keyboard overlap
                val displayMetrics = resources.displayMetrics
                bs.layoutParams.height = displayMetrics.heightPixels
            }
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAddHabitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        habitId = arguments?.getLong(ARG_HABIT_ID) ?: -1L
        val isEditMode = habitId != -1L

        setupSuggestions()
        setupDaySelection()
        
        if (isEditMode) {
            prefillHabitData()
            binding.tvAddHabitTitle.text = "Edit Habit"
            binding.btnSaveHabit.text = "Update Habit"
        }

        updateActiveDaysHint()
        updateTimeDisplay()

        // Focus and show keyboard for name
        binding.etHabitName.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etHabitName, InputMethodManager.SHOW_IMPLICIT)

        binding.etHabitName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val suggestion = EmojiSuggester.suggest(s.toString())
                binding.etEmoji.setText(suggestion)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnPickTime.setOnClickListener {
            SoundManager.playTick()
            VibrationManager.vibrateSubtle(requireContext())
            TimePickerDialog(requireContext(), { _, hour, minute ->
                notifHour = hour
                notifMinute = minute
                updateTimeDisplay()
            }, notifHour, notifMinute, true).show()
        }

        binding.togglePriority.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) SoundManager.playTick()
        }

        binding.btnSaveHabit.setOnClickListener {
            VibrationManager.vibrateMedium(requireContext())
            val name = binding.etHabitName.text.toString().trim()
            val emoji = binding.etEmoji.text.toString().trim().ifEmpty { "🔥" }
            if (name.isEmpty()) {
                Toast.makeText(context, "Please enter a habit name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val priority = when (binding.togglePriority.checkedButtonId) {
                com.lockedinbeta.R.id.btnPriorityHigh -> 0
                com.lockedinbeta.R.id.btnPriorityLow -> 2
                else -> 1
            }
            val targetCount = binding.etTargetCount.text.toString().toIntOrNull() ?: 1
            
            // Calculate activeDays bitmask
            var activeDays = 0
            if (binding.btnMon.isChecked) activeDays = activeDays or 1
            if (binding.btnTue.isChecked) activeDays = activeDays or 2
            if (binding.btnWed.isChecked) activeDays = activeDays or 4
            if (binding.btnThu.isChecked) activeDays = activeDays or 8
            if (binding.btnFri.isChecked) activeDays = activeDays or 16
            if (binding.btnSat.isChecked) activeDays = activeDays or 32
            if (binding.btnSun.isChecked) activeDays = activeDays or 64
            if (activeDays == 0) activeDays = 127 // Default to all days if none selected

            if (habitId != -1L) {
                // Edit Mode
                val existingHabit = viewModel.allHabits.value?.find { it.id == habitId }
                if (existingHabit != null) {
                    val updated = existingHabit.copy(
                        name = name,
                        emoji = emoji,
                        notificationHour = notifHour,
                        notificationMinute = notifMinute,
                        priority = priority,
                        targetCount = targetCount,
                        activeDays = activeDays
                    )
                    viewModel.updateHabitDetails(updated)
                }
            } else {
                // New Mode
                SoundManager.playChime()
                viewModel.addHabit(name, emoji, notifHour, notifMinute, priority, targetCount, activeDays)
            }
            dismiss()
        }
    }

    private fun prefillHabitData() {
        val habit = viewModel.allHabits.value?.find { it.id == habitId } ?: return
        binding.etHabitName.setText(habit.name)
        binding.etEmoji.setText(habit.emoji)
        binding.etTargetCount.setText(habit.targetCount.toString())
        notifHour = habit.notificationHour
        notifMinute = habit.notificationMinute
        
        val priorityId = when (habit.priority) {
            0 -> com.lockedinbeta.R.id.btnPriorityHigh
            2 -> com.lockedinbeta.R.id.btnPriorityLow
            else -> com.lockedinbeta.R.id.btnPriorityMed
        }
        binding.togglePriority.check(priorityId)
        
        // Days
        binding.toggleDays.clearChecked()
        if ((habit.activeDays and 1) != 0) binding.toggleDays.check(com.lockedinbeta.R.id.btnMon)
        if ((habit.activeDays and 2) != 0) binding.toggleDays.check(com.lockedinbeta.R.id.btnTue)
        if ((habit.activeDays and 4) != 0) binding.toggleDays.check(com.lockedinbeta.R.id.btnWed)
        if ((habit.activeDays and 8) != 0) binding.toggleDays.check(com.lockedinbeta.R.id.btnThu)
        if ((habit.activeDays and 16) != 0) binding.toggleDays.check(com.lockedinbeta.R.id.btnFri)
        if ((habit.activeDays and 32) != 0) binding.toggleDays.check(com.lockedinbeta.R.id.btnSat)
        if ((habit.activeDays and 64) != 0) binding.toggleDays.check(com.lockedinbeta.R.id.btnSun)
    }

    private fun setupSuggestions() {
        val commonHabits = listOf(
            "Gym", "Run", "Walk", "Yoga", "Read", "Meditation", "Journal", "Code", 
            "Study", "Water", "Cook", "Clean", "No alcohol", "No smoking"
        )
        
        commonHabits.forEach { habitName ->
            val chip = Chip(requireContext()).apply {
                text = habitName
                isCheckable = true
                setOnClickListener {
                    SoundManager.playTick()
                    binding.etHabitName.setText(habitName)
                    binding.etEmoji.setText(EmojiSuggester.suggest(habitName))
                }
            }
            binding.chipGroupHabitSuggestions.addView(chip)
        }
    }

    private fun setupDaySelection() {
        // Check all by default
        binding.toggleDays.check(com.lockedinbeta.R.id.btnMon)
        binding.toggleDays.check(com.lockedinbeta.R.id.btnTue)
        binding.toggleDays.check(com.lockedinbeta.R.id.btnWed)
        binding.toggleDays.check(com.lockedinbeta.R.id.btnThu)
        binding.toggleDays.check(com.lockedinbeta.R.id.btnFri)
        binding.toggleDays.check(com.lockedinbeta.R.id.btnSat)
        binding.toggleDays.check(com.lockedinbeta.R.id.btnSun)
        
        binding.toggleDays.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) SoundManager.playTick()
            updateActiveDaysHint()
        }
    }

    private fun updateActiveDaysHint() {
        val mon = binding.btnMon.isChecked
        val tue = binding.btnTue.isChecked
        val wed = binding.btnWed.isChecked
        val thu = binding.btnThu.isChecked
        val fri = binding.btnFri.isChecked
        val sat = binding.btnSat.isChecked
        val sun = binding.btnSun.isChecked

        val hint = when {
            mon && tue && wed && thu && fri && sat && sun -> "Daily"
            mon && tue && wed && thu && fri && !sat && !sun -> "Weekdays only"
            !mon && !tue && !wed && !thu && !fri && sat && sun -> "Weekends only"
            mon && !tue && wed && !thu && fri && !sat && sun -> "Every other day"
            else -> "Custom schedule"
        }
        binding.tvActiveDaysHint.text = "Repeat on ($hint)"
    }

    private fun updateTimeDisplay() {
        val amPm = if (notifHour < 12) "AM" else "PM"
        val displayHour = when {
            notifHour == 0 -> 12
            notifHour > 12 -> notifHour - 12
            else -> notifHour
        }
        binding.tvSelectedTime.text = "Reminder: %d:%02d %s".format(displayHour, notifMinute, amPm)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
