package com.streakapp.ui.habits

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.lockedinbeta.R
import com.lockedinbeta.databinding.ActivityMainBinding
import com.streakapp.notifications.NotificationScheduler

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    val viewModel: HabitViewModel by viewModels {
        HabitViewModelFactory(application)
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* permission result handled silently */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Apply custom font to Logo
        val logoFont = ResourcesCompat.getFont(this, R.font.inter_bold)
        binding.toolbarTitle.typeface = logoFont

        requestNotificationPermission()
        setupViewPager()
        observeMessages()
        startQuoteAnimation()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = MainPagerAdapter(this)
    }

    private fun observeMessages() {
        viewModel.message.observe(this) { msg ->
            if (msg.contains("done!") && msg.contains("🔥")) {
                handleStreakCelebration(msg)
            } else {
                showStyledSnackbar(msg)
            }
        }
    }

    private fun showStyledSnackbar(message: String) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
        val tv = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        tv.typeface = ResourcesCompat.getFont(this, R.font.inter)
        snackbar.show()
    }

    private fun showToolbarMessage(message: String, color: Int, durationMs: Long = 3000) {
        binding.toolbarBrandContainer.animate()
            .alpha(0f)
            .setDuration(400)
            .withEndAction {
                binding.toolbarLogo.visibility = android.view.View.GONE
                binding.toolbarTitle.text = message
                binding.toolbarTitle.setTextColor(color)
                binding.toolbarTitle.typeface = ResourcesCompat.getFont(this, R.font.inter_bold)
                
                binding.toolbarBrandContainer.animate()
                    .alpha(1f)
                    .setDuration(400)
                    .withEndAction {
                        binding.toolbarBrandContainer.postDelayed({
                            binding.toolbarBrandContainer.animate()
                                .alpha(0f)
                                .setDuration(400)
                                .withEndAction {
                                    binding.toolbarLogo.visibility = android.view.View.VISIBLE
                                    binding.toolbarTitle.text = "Streaks"
                                    binding.toolbarTitle.setTextColor(
                                        com.google.android.material.color.MaterialColors.getColor(binding.toolbarTitle, com.google.android.material.R.attr.colorOnSurface)
                                    )
                                    binding.toolbarTitle.typeface = ResourcesCompat.getFont(this, R.font.inter_bold)
                                    
                                    binding.toolbarBrandContainer.animate()
                                        .alpha(1f)
                                        .setDuration(400)
                                        .start()
                                }.start()
                        }, durationMs)
                    }.start()
            }.start()
    }

    fun handleStreakCelebration(msg: String) {
        val parts = msg.split(" ")
        val streak = parts.find { it.toIntOrNull() != null }?.toInt() ?: return
        
        val isMilestone = when (streak) {
            7 -> true
            14 -> true
            30 -> true
            60 -> true
            90 -> true
            100 -> true
            365 -> true
            else -> (streak % 50 == 0)
        }
        
        if (isMilestone) {
            triggerVibration()
            binding.confettiView.burst()
            val congrats = when (streak) {
                7 -> "1 Week! Keep it up! 🚀"
                14 -> "2 Weeks! You're on fire! 🔥"
                30 -> "1 Month! Built Different! 😤"
                else -> "$streak days! UNSTOPPABLE! 🏆"
            }
            showToolbarMessage(congrats, Color.parseColor("#FFD700"), 3000) // Gold for milestones
        } else {
            showStyledSnackbar(msg)
        }
    }

    private fun triggerVibration() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 100, 50, 100, 50, 100)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun startQuoteAnimation() {
        val prefs = getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)
        val openCount = prefs.getInt("app_open_count", 0) + 1
        prefs.edit().putInt("app_open_count", openCount).apply()

        val isMilestone = openCount == 10 || openCount == 20 || openCount == 30
        val showEasterEgg = isMilestone || Math.random() < 0.5 

        val quotes = listOf(
            "Don't stop now.", "You showed up.", "Keep the fire alive.",
            "One more day.", "Still here. Still going.", "Don't break the chain.",
            "You know what to do.", "Make it count.", "Stay consistent.",
            "Do it anyway.", "No days off.", "Prove it to yourself.",
            "The streak doesn't lie.", "Lock in.", "Built different."
        )
        val easterEggs = listOf(
            "do it Kakarot", "This is my peak", "Domain Expansion", "Cesaer!!!!!"
        )

        val message = if (showEasterEgg) easterEggs.random() else quotes.random()

        // Get highest streak to determine color for normal quotes
        val habits = viewModel.allHabits.value ?: emptyList()
        val maxStreak = habits.maxOfOrNull { it.currentStreak } ?: 0
        val quoteColor = when {
            maxStreak >= 60 -> Color.parseColor("#8800FF") // Purple
            maxStreak >= 30 -> Color.parseColor("#0088FF") // Blue
            else -> Color.parseColor("#FF6D00") // Red/Orange
        }
        
        val finalColor = if (showEasterEgg) Color.parseColor("#FFD700") else quoteColor // Gold for easter eggs
        
        if (showEasterEgg) triggerVibration()
        
        binding.toolbarBrandContainer.postDelayed({
            showToolbarMessage(message, finalColor, 2000)
        }, 300)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_all_stats -> {
                AllStatsFragment().show(supportFragmentManager, "AllStats")
                true
            }
            R.id.action_settings -> {
                SettingsBottomSheet().show(supportFragmentManager, "Settings")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
