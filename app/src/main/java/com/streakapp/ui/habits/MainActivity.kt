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
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.lockedinbeta.R
import com.lockedinbeta.databinding.ActivityMainBinding
import com.streakapp.VibrationManager
import com.streakapp.SoundManager
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
        
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "Streaks" else "History"
        }.attach()

        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                SoundManager.playTick()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // Toolbar title is now static "LockedIn"
            }
        })
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
        // Temporarily hide menu items to make room for long quotes
        binding.toolbar.menu.setGroupVisible(0, false)
        
        binding.toolbarBrandContainer.animate()
            .alpha(0f)
            .setDuration(400)
            .withEndAction {
                binding.toolbarLogo.visibility = android.view.View.GONE
                binding.toolbarTitle.text = message
                binding.toolbarTitle.setTextColor(color)
                binding.toolbarTitle.textSize = 14f // Smaller text for longer quotes
                binding.toolbarTitle.typeface = ResourcesCompat.getFont(this, R.font.inter_bold)
                
                // Add subtle glow
                binding.toolbarTitle.setShadowLayer(15f, 0f, 0f, color)
                
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
                                    binding.toolbarTitle.text = "LockedIn"
                                    binding.toolbarTitle.textSize = 20f // Back to normal
                                    
                                    // Remove glow
                                    binding.toolbarTitle.setShadowLayer(0f, 0f, 0f, 0)

                                    binding.toolbarTitle.setTextColor(
                                        com.google.android.material.color.MaterialColors.getColor(binding.toolbarTitle, com.google.android.material.R.attr.colorOnSurface)
                                    )
                                    binding.toolbarTitle.typeface = ResourcesCompat.getFont(this, R.font.inter_bold)
                                    
                                    binding.toolbarBrandContainer.animate()
                                        .alpha(1f)
                                        .setDuration(400)
                                        .withEndAction {
                                            // Show menu items again
                                            binding.toolbar.menu.setGroupVisible(0, true)
                                        }
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
            VibrationManager.vibrateMilestone(this)
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

    // Removed triggerVibration, logic moved to VibrationManager

    private fun startQuoteAnimation() {
        val prefs = getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)
        val openCount = prefs.getInt("app_open_count", 0) + 1
        prefs.edit()
            .putInt("app_open_count", openCount)
            .putLong("last_open_timestamp", System.currentTimeMillis())
            .apply()

        val isMilestone = openCount == 10 || openCount == 20 || openCount == 30
        val showEasterEgg = isMilestone || Math.random() < 0.5 

        val quotes = listOf(
            "\"The pain of discipline is far less than the pain of regret.\"",
            "\"You don't have to be great to start, but you have to start to be great.\"",
            "\"Success is the sum of small efforts, repeated day in and day out.\"",
            "\"It does not matter how slowly you go as long as you do not stop.\"",
            "\"The secret of your future is hidden in your daily routine.\"",
            "\"Consistency is what transforms average into excellence.\"",
            "\"Do something today that your future self will thank you for.\"",
            "\"Your habits will either make you or break you. Choose wisely.\"",
            "\"Small daily improvements over time lead to stunning results.\"",
            "\"The only way to achieve the impossible is to believe it is possible.\"",
            "\"Don't stop when you're tired. Stop when you're done.\"",
            "\"Hard work beats talent when talent doesn't work hard.\"",
            "\"Action is the foundational key to all success.\"",
            "\"Discipline is doing what needs to be done, even if you don't want to.\"",
            "\"The best time to plant a tree was 20 years ago. The second best time is now.\""
        )
        val easterEggs = listOf(
            "\"Strength is the only thing that matters in this world. Everything else is just a delusion for the weak.\"",
            "\"If you don't like your destiny, don't accept it. Instead, have the courage to change it.\"",
            "\"It’s not the face that makes someone a monster; it’s the choices they make with their lives.\"",
            "\"Power is not will, it is the phenomenon of physically making things happen.\"",
            "\"I must go beyond my limits. That is what it means to be a hero!\""
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
        
        if (showEasterEgg) VibrationManager.vibrateStrong(this)
        
        binding.toolbarBrandContainer.postDelayed({
            showToolbarMessage(message, finalColor, 2000)
        }, 300)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        SoundManager.playTick()
        return when (item.itemId) {
            R.id.action_all_stats -> {
                if (com.streakapp.DevModeManager.isDevModeEnabled) {
                    com.streakapp.DevModeManager.dateOffsetDays++
                    val newDate = com.streakapp.DevModeManager.getDevToday()
                    showStyledSnackbar("Dev: Day advanced to $newDate")
                    recreate() 
                } else {
                    AllStatsFragment().show(supportFragmentManager, "AllStats")
                }
                true
            }
            R.id.action_settings -> {
                // Find the settings view in toolbar and rotate it
                binding.toolbar.findViewById<View>(R.id.action_settings)?.animate()
                    ?.rotationBy(360f)
                    ?.setDuration(500)
                    ?.start()

                SettingsBottomSheet().show(supportFragmentManager, "Settings")
                true
            }
            R.id.sort_manual -> {
                viewModel.setSortOrder(HabitViewModel.SortOrder.USER_ORDER)
                true
            }
            R.id.sort_time -> {
                viewModel.setSortOrder(HabitViewModel.SortOrder.TIME_REMAINING)
                true
            }
            R.id.sort_importance -> {
                viewModel.setSortOrder(HabitViewModel.SortOrder.IMPORTANCE)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
