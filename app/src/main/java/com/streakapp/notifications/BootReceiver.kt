package com.streakapp.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.streakapp.StreakApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val repo = (context.applicationContext as StreakApplication).repository
        CoroutineScope(Dispatchers.IO).launch {
            repo.getAllHabitsOnce().forEach { habit ->
                NotificationScheduler.scheduleNotification(context, habit)
            }
        }
    }
}
