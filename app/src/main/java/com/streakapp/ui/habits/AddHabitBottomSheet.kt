package com.streakapp.ui.habits

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lockedinbeta.databinding.BottomSheetAddHabitBinding
import java.util.Calendar

class AddHabitBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddHabitBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HabitViewModel by activityViewModels {
        HabitViewModelFactory(requireActivity().application)
    }

    private var notifHour = 20
    private var notifMinute = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAddHabitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateTimeDisplay()

        binding.btnPickTime.setOnClickListener {
            TimePickerDialog(requireContext(), { _, hour, minute ->
                notifHour = hour
                notifMinute = minute
                updateTimeDisplay()
            }, notifHour, notifMinute, true).show()
        }

        binding.btnSaveHabit.setOnClickListener {
            val name = binding.etHabitName.text.toString().trim()
            val emoji = binding.etEmoji.text.toString().trim().ifEmpty { "🔥" }
            if (name.isEmpty()) {
                Toast.makeText(context, "Please enter a habit name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.addHabit(name, emoji, notifHour, notifMinute)
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
