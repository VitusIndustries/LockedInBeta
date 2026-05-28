package com.streakapp.ui.habits

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lockedinbeta.databinding.BottomSheetSettingsBinding
import com.streakapp.DevModeManager

class SettingsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSettingsBinding? = null
    private val binding get() = _binding!!
    private var tapCount = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)

        // Dark Mode
        binding.switchDarkMode.isChecked = prefs.getBoolean("dark_mode", false)
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            dismiss()
        }

        // Vibration
        binding.switchVibration.isChecked = prefs.getBoolean("vibration", true)
        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("vibration", isChecked).apply()
        }

        // Vacation Mode
        binding.switchVacation.isChecked = prefs.getBoolean("vacation_mode", false)
        binding.switchVacation.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("vacation_mode", isChecked).apply()
        }

        // Minimalist Mode
        binding.switchMinimalist.isChecked = prefs.getBoolean("minimalist_mode", false)
        binding.switchMinimalist.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("minimalist_mode", isChecked).apply()
            (activity as? MainActivity)?.recreate()
        }

        binding.layoutFooter.setOnClickListener {
            tapCount++
            if (tapCount >= 7) {
                DevModeManager.isDevModeEnabled = !DevModeManager.isDevModeEnabled
                val status = if (DevModeManager.isDevModeEnabled) "ENABLED" else "DISABLED"
                android.widget.Toast.makeText(context, "Dev Mode $status", android.widget.Toast.LENGTH_SHORT).show()
                tapCount = 0
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
