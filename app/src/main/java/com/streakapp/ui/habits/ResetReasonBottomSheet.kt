package com.streakapp.ui.habits

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lockedinbeta.databinding.BottomSheetResetReasonBinding
import com.streakapp.StreakApplication
import com.streakapp.VibrationManager
import com.streakapp.data.model.Habit
import com.streakapp.data.model.HabitCompletion
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ResetReasonBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetResetReasonBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HabitViewModel by activityViewModels {
        HabitViewModelFactory(requireActivity().application)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }

    companion object {
        private const val ARG_HABIT_ID = "habit_id"
        fun newInstance(habitId: Long) = ResetReasonBottomSheet().apply {
            arguments = Bundle().apply { putLong(ARG_HABIT_ID, habitId) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetResetReasonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false // Force user to address the reset

        val habitId = arguments?.getLong(ARG_HABIT_ID) ?: return
        val repo = (requireActivity().application as StreakApplication).repository

        lifecycleScope.launch {
            val habit = repo.getHabitById(habitId) ?: return@launch
            binding.tvSubtitle.text = "Your streak for ${habit.name} has ended. Why did you fall off?"
            val remaining = 2 - habit.recoveryChancesUsed
            binding.tvRecoveryCount.text = "Chances left: $remaining/2"
            binding.btnRecover.isEnabled = remaining > 0

            binding.chipLazy.setOnClickListener { binding.etReason.setText("I got Lazy") }
            binding.chipBusy.setOnClickListener { binding.etReason.setText("I was busy") }
            binding.chipWeakness.setOnClickListener { binding.etReason.setText("Moment of weakness") }

            binding.btnSaveReason.setOnClickListener {
                VibrationManager.vibrateMedium(requireContext())
                val reason = binding.etReason.text.toString().trim()
                if (reason.isEmpty()) {
                    Toast.makeText(context, "Please enter a reason", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                lifecycleScope.launch {
                    // Update habit with reason and reset streak (logic handled by recalculateStreak usually)
                    // But here we explicitly set the reason.
                    repo.saveResetReason(habit, reason)
                    dismiss()
                }
            }

            binding.btnRecover.setOnClickListener {
                VibrationManager.vibrateStrong(requireContext())
                lifecycleScope.launch {
                    val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
                    repo.recoverStreak(habit, yesterday)
                    Toast.makeText(context, "Streak Recovered!", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
