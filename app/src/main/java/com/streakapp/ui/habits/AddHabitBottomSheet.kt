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
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lockedinbeta.databinding.BottomSheetAddHabitBinding
import com.streakapp.VibrationManager
import java.util.Calendar

class AddHabitBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddHabitBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HabitViewModel by activityViewModels {
        HabitViewModelFactory(requireActivity().application)
    }

    private var notifHour = 20
    private var notifMinute = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAddHabitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
            VibrationManager.vibrateSubtle(requireContext())
            TimePickerDialog(requireContext(), { _, hour, minute ->
                notifHour = hour
                notifMinute = minute
                updateTimeDisplay()
            }, notifHour, notifMinute, true).show()
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
            viewModel.addHabit(name, emoji, notifHour, notifMinute, priority, targetCount)
            dismiss()
        }
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
