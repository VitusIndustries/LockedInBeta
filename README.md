# 🔥 StreakApp — Native Android Habit Tracker

A clean, native Android habit streak tracker built with Kotlin, Room, WorkManager, and Jetpack Glance.

---

## Features

- ✅ Add & delete custom habits with emoji icons
- ✅ Daily check-in — mark habits done with one tap
- ✅ Streak counter — auto-resets if you miss a day
- ✅ Per-habit stats with a custom calendar heatmap
- ✅ All-habits overview with a streak leaderboard
- ✅ Daily push notifications — per habit, at your chosen time
- ✅ Home screen widget — see streaks & complete habits without opening the app

---

## Tech Stack

| Layer | Library |
|---|---|
| Database | Room 2.6 |
| Background work | WorkManager 2.9 |
| Home screen widget | Jetpack Glance 1.0 |
| Architecture | MVVM + Repository |
| UI | Material3, ViewBinding |

---

## Setup in Android Studio

### 1. Open the Project
- Launch Android Studio
- Choose **File → Open** and select the `StreakApp` folder
- Wait for Gradle sync to complete

### 2. Add a Launcher Icon
Android Studio needs launcher icons in `res/mipmap-*` folders.
- Right-click `res` → **New → Image Asset**
- Choose any icon you like (or use the default Android one)
- Click **Finish** — this generates all mipmap sizes automatically

### 3. Run the App
- Connect an Android device (API 26+) or start an emulator
- Press the **Run ▶** button

---

## How the Streak Logic Works

- When you tap "Mark Done", today's date is recorded in the `habit_completions` table
- `recalculateStreak()` walks backward from today, counting consecutive days
- If yesterday isn't in the completions, streak resets to 0 (or 1 if today is done)
- Tapping again **undoes** today's completion

---

## Adding the Home Screen Widget

1. Long-press your Android home screen
2. Tap **Widgets**
3. Find **Streaks** and drag it to your screen
4. Tap the ✓ button next to any habit to mark it done without opening the app

---

## Notification Scheduling

Each habit gets its own daily WorkManager `PeriodicWorkRequest`. The reminder:
- Fires at the time you set when creating the habit (default 8:00 PM)
- Skips the notification if you've already completed the habit that day
- Reschedules automatically after device reboot via `BootReceiver`

---

## Project Structure

```
app/src/main/java/com/streakapp/
├── StreakApplication.kt          # App class, DB init, notification channel
├── data/
│   ├── model/
│   │   ├── Habit.kt              # Room entity
│   │   └── HabitCompletion.kt   # Completion history entity
│   ├── db/
│   │   ├── HabitDao.kt           # All DB queries
│   │   └── StreakDatabase.kt     # Room database
│   └── repository/
│       └── HabitRepository.kt   # Business logic + streak calc
├── ui/
│   ├── habits/
│   │   ├── MainActivity.kt
│   │   ├── HabitViewModel.kt
│   │   ├── HabitAdapter.kt
│   │   ├── AddHabitBottomSheet.kt
│   │   ├── StatsFragment.kt
│   │   └── AllStatsFragment.kt
│   ├── stats/
│   │   ├── StatsViewModel.kt
│   │   └── StreakCalendarView.kt  # Custom calendar heatmap
│   └── widget/
│       └── HabitWidget.kt        # Glance home screen widget
└── notifications/
    ├── NotificationScheduler.kt
    ├── HabitReminderWorker.kt
    └── BootReceiver.kt
```

---

## Possible Next Features

- [ ] Habit reordering (drag & drop)
- [ ] Weekly/monthly completion graphs
- [ ] Habit categories / tags
- [ ] Backup & restore to Google Drive
- [ ] Dark mode support
- [ ] Streak freeze (grace day)
