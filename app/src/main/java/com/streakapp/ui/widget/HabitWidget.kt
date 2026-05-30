package com.streakapp.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.updateAll
import com.streakapp.StreakApplication
import com.streakapp.data.model.Habit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val habitIdKey = ActionParameters.Key<Long>("habit_id")

class HabitWidget : GlanceAppWidget() {

    // Optimized sizing
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(androidx.compose.ui.unit.DpSize(100.dp, 100.dp), androidx.compose.ui.unit.DpSize(250.dp, 250.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = (context.applicationContext as StreakApplication).repository
        val habits = repo.getAllHabitsOnce()
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        
        val prefs = context.getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)
        val isVacationMode = prefs.getBoolean("vacation_mode", false)

        provideContent {
            // GlanceTheme handles system Dark/Light mode automatically
            GlanceTheme {
                WidgetContent(habits = habits, today = today, isVacationMode = isVacationMode)
            }
        }
    }
}

@Composable
private fun WidgetContent(habits: List<Habit>, today: String, isVacationMode: Boolean) {
    val streakEmoji = if (isVacationMode) "❄️" else "🔥"
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(12.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$streakEmoji Streaks",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = GlanceTheme.colors.onSurface
                ),
                modifier = GlanceModifier.defaultWeight()
            )
        }

        if (habits.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No habits yet",
                    style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        } else {
            // LazyColumn is better for performance if many habits exist
            androidx.glance.appwidget.lazy.LazyColumn {
                items(habits.size) { index ->
                    val habit = habits[index]
                    HabitRow(
                        habit = habit, 
                        doneToday = habit.lastCompletedDate == today,
                        streakEmoji = streakEmoji
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitRow(habit: Habit, doneToday: Boolean, streakEmoji: String) {
    val streakColor = when {
        habit.currentStreak >= 60 -> Color(0xFF9C27B0)
        habit.currentStreak >= 30 -> Color(0xFF03A9F4)
        else -> Color(0xFFFF6D00)
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${habit.emoji} ${habit.name}",
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                fontSize = 14.sp,
                color = GlanceTheme.colors.onSurface
            ),
            maxLines = 1
        )

        Text(
            text = "${habit.currentStreak} $streakEmoji",
            modifier = GlanceModifier.padding(horizontal = 8.dp),
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ColorProvider(streakColor)
            )
        )

        val checkColor = if (doneToday) streakColor else Color(0xFFE0E0E0)
        
        Box(
            modifier = GlanceModifier
                .size(32.dp)
                .background(ColorProvider(checkColor))
                .clickable(
                    actionRunCallback<CompleteHabitAction>(
                        parameters = actionParametersOf(habitIdKey to habit.id)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (doneToday) "✓" else "○",
                style = TextStyle(
                    color = ColorProvider(if (doneToday) Color.White else Color(0xFF9E9E9E)),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

class CompleteHabitAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val habitId = parameters[habitIdKey] ?: return
        val repo = (context.applicationContext as StreakApplication).repository
        val habit = repo.getHabitById(habitId) ?: return
        
        // Pass vacation mode if applicable from prefs
        val prefs = context.getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)
        val isVacationMode = prefs.getBoolean("vacation_mode", false)
        
        repo.toggleTodayCompletion(habit, isVacationMode)
        HabitWidget().update(context, glanceId)
    }
}

class HabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidget()
}
