package com.streakapp.ui.habits

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lockedinbeta.databinding.BottomSheetSettingsBinding
import com.streakapp.DevModeManager
import com.streakapp.VibrationManager
import com.streakapp.SoundManager
import com.streakapp.ui.widget.HabitWidget
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.streakapp.data.model.Habit
import kotlinx.coroutines.launch

class SettingsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSettingsBinding? = null
    private val binding get() = _binding!!
    private var tapCount = 0
    
    private val viewModel: HabitViewModel by activityViewModels {
        HabitViewModelFactory(requireActivity().application)
    }

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
            SoundManager.playTick()
            VibrationManager.vibrateSubtle(requireContext())
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
            SoundManager.playTick()
            prefs.edit().putBoolean("vibration", isChecked).apply()
            if (isChecked) VibrationManager.vibrateMedium(requireContext())
        }

        // Vacation Mode
        binding.switchVacation.isChecked = prefs.getBoolean("vacation_mode", false)
        binding.switchVacation.setOnCheckedChangeListener { _, isChecked ->
            SoundManager.playTick()
            VibrationManager.vibrateSubtle(requireContext())
            prefs.edit().putBoolean("vacation_mode", isChecked).apply()
            // Refresh widget to show snowflakes/fire immediately
            lifecycleScope.launch {
                HabitWidget().updateAll(requireContext())
            }
        }

        // Minimalist Mode
        binding.switchMinimalist.isChecked = prefs.getBoolean("minimalist_mode", false)
        binding.switchMinimalist.setOnCheckedChangeListener { _, isChecked ->
            SoundManager.playTick()
            VibrationManager.vibrateSubtle(requireContext())
            prefs.edit().putBoolean("minimalist_mode", isChecked).apply()
            (activity as? MainActivity)?.recreate()
        }

        setupArchiveList()

        binding.layoutFooter.setOnClickListener {
            VibrationManager.vibrateSubtle(requireContext())
            tapCount++
            if (tapCount >= 7) {
                VibrationManager.vibrateStrong(requireContext())
                DevModeManager.isDevModeEnabled = !DevModeManager.isDevModeEnabled
                val status = if (DevModeManager.isDevModeEnabled) "ENABLED" else "DISABLED"
                android.widget.Toast.makeText(context, "Dev Mode $status", android.widget.Toast.LENGTH_SHORT).show()
                tapCount = 0
            }
        }
    }

    private fun setupArchiveList() {
        val adapter = ArchiveAdapter { habit ->
            viewModel.unarchiveHabit(habit)
        }
        binding.recyclerArchive.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.recyclerArchive.adapter = adapter
        
        viewModel.archivedHabits.observe(viewLifecycleOwner) { habits ->
            adapter.submitList(habits)
        }
    }

    private class ArchiveAdapter(private val onReinstate: (Habit) -> Unit) : 
        androidx.recyclerview.widget.ListAdapter<Habit, ArchiveAdapter.VH>(HabitAdapter.DiffCallback) {
        
        class VH(val b: com.lockedinbeta.databinding.ItemHistoryBinding) : RecyclerView.ViewHolder(b.root)
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            com.lockedinbeta.databinding.ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
        
        override fun onBindViewHolder(holder: VH, position: Int) {
            val h = getItem(position)
            holder.b.tvHistoryName.text = h.name
            holder.b.tvHistoryEmoji.text = h.emoji
            holder.b.tvHistoryStreak.text = "Revive"
            holder.b.tvHistoryStreak.setOnClickListener { 
                SoundManager.playTick()
                VibrationManager.vibrateMedium(holder.b.root.context)
                onReinstate(h) 
            }
            holder.b.tvHistoryDate.visibility = View.GONE
            holder.b.tvHistoryReason.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
