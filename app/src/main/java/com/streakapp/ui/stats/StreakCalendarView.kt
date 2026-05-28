package com.streakapp.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class StreakCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private var streakData: Map<String, Int> = emptyMap()

    private val paintCell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 34f // Increased from 28f
        isFakeBoldText = true
    }
    private val paintHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDarkMode()) Color.WHITE else Color.parseColor("#212121")
        textAlign = Paint.Align.CENTER
        textSize = 32f
        isFakeBoldText = true
    }

    private val month = YearMonth.now()
    private val today = LocalDate.now()

    private fun isDarkMode(): Boolean {
        return (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    fun setStreakData(data: Map<String, Int>) {
        streakData = data
        invalidate()
    }

    fun setCompletedDates(dates: Set<String>) {
        streakData = dates.associateWith { 1 }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val cellSize = width / 7f
        val firstDayOfWeek = (month.atDay(1).dayOfWeek.value - 1) % 7
        val rows = (month.lengthOfMonth() + firstDayOfWeek + 6) / 7
        val height = (cellSize * rows + 100).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cellSize = width / 7f
        val radius = cellSize * 0.45f // Increased from 0.38f to fill more space

        val days = listOf("M", "T", "W", "T", "F", "S", "S")
        paintHeader.color = if (isDarkMode()) Color.WHITE else Color.parseColor("#212121")
        days.forEachIndexed { i, d ->
            canvas.drawText(d, cellSize * i + cellSize / 2, 50f, paintHeader)
        }

        val firstDayOfWeek = (month.atDay(1).dayOfWeek.value - 1) % 7
        val totalDays = month.lengthOfMonth()

        for (day in 1..totalDays) {
            val index = firstDayOfWeek + day - 1
            val col = index % 7
            val row = index / 7

            val cx = col * cellSize + cellSize / 2
            val cy = 80 + row * cellSize + cellSize / 2
            val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

            val date = month.atDay(day).format(dateFormatter)
            val currentStreak = streakData[date] ?: 0
            val isToday = month.atDay(day) == today

            val cellColor = when {
                currentStreak >= 30 -> Color.parseColor("#F44336")
                currentStreak >= 21 -> Color.parseColor("#FFEB3B")
                currentStreak >= 7 -> Color.parseColor("#4CAF50")
                currentStreak > 0 -> Color.parseColor("#FF6D00")
                else -> if (isDarkMode()) Color.parseColor("#333333") else Color.parseColor("#E0E0E0")
            }

            paintCell.color = cellColor
            canvas.drawRoundRect(rect, radius, radius, paintCell)

            // Dynamic text color for readability in Dark/Light mode
            paintText.color = when {
                currentStreak >= 21 && currentStreak < 30 -> Color.BLACK // Black on Yellow
                currentStreak > 0 -> Color.WHITE // White on Red/Green/Orange
                isDarkMode() -> Color.parseColor("#BBBBBB") // Light grey on Dark grey
                else -> Color.parseColor("#9E9E9E") // Dark grey on Light grey
            }
            
            val textY = cy + (paintText.textSize / 3) // Vertically centered
            canvas.drawText(day.toString(), cx, textY, paintText)
            
            // Highlight today with a small dot or stroke if not completed
            if (isToday && currentStreak == 0) {
                paintCell.style = Paint.Style.STROKE
                paintCell.strokeWidth = 4f
                paintCell.color = Color.parseColor("#FF6D00")
                canvas.drawRoundRect(rect, radius, radius, paintCell)
                paintCell.style = Paint.Style.FILL
            }
        }
    }
}
