package com.streakapp.ui.fire

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*
import kotlin.random.Random

/**
 * Optimized "Vibrant Fire" animation.
 * Removed Software Layer dependency and BlurMaskFilter for high-performance rendering.
 */
class CartoonFireView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var streak: Int = 0
    private var time: Float = 0f
    private val random = Random
    
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flamePath = Path()

    private class FireParticle(
        var x: Float,
        var y: Float,
        var size: Float,
        var vy: Float,
        var life: Float,
        var color: Int,
        var drift: Float,
        var phase: Float
    )

    private val particles = mutableListOf<FireParticle>()

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = Long.MAX_VALUE
        interpolator = LinearInterpolator()
        addUpdateListener { 
            time += 0.02f // Slowed down from 0.05f
            updatePhysics()
            invalidate() 
        }
    }

    fun setStreak(s: Int) {
        streak = s
        if (streak > 0) {
            if (!animator.isRunning) animator.start()
        } else {
            animator.cancel()
            particles.clear()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (streak > 0 && !animator.isRunning) animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    private fun updatePhysics() {
        if (streak <= 0 || width == 0) return

        // Optimized Color Retrieval
        val mainColor = when {
            streak >= 60 -> 0xFF9C27B0.toInt() // Purple
            streak >= 30 -> 0xFF03A9F4.toInt() // Blue
            else -> 0xFFFF5722.toInt() // Orange/Red
        }
        val innerColor = when {
            streak >= 60 -> 0xFFE1BEE7.toInt()
            streak >= 30 -> 0xFFB3E5FC.toInt()
            else -> 0xFFFFD54F.toInt() // Yellow/Gold
        }

        // Reduced spawn rate for performance
        val spawnRate = (2 + streak / 10).coerceAtMost(10)
        repeat(spawnRate) {
            particles.add(FireParticle(
                x = width / 2f + (random.nextFloat() - 0.5f) * width * 0.8f,
                y = height.toFloat(),
                size = (15f + random.nextFloat() * 20f) * (1f + streak / 150f),
                vy = -(4f + random.nextFloat() * 8f) * (1f + streak / 120f),
                life = 1.0f,
                color = if (random.nextFloat() > 0.7f) innerColor else mainColor,
                drift = (random.nextFloat() - 0.5f) * 4f,
                phase = random.nextFloat() * PI.toFloat() * 2f
            ))
        }

        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.y += p.vy
            p.x += p.drift + sin(p.y * 0.05f + time + p.phase) * 3f
            p.life -= 0.03f
            p.size *= 0.96f
            if (p.life <= 0 || p.size < 2f) it.remove()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (streak <= 0 || width == 0 || particles.isEmpty()) return

        // Single pass for flames to avoid multiple loops
        for (p in particles) {
            particlePaint.color = p.color
            particlePaint.alpha = (p.life * 200).toInt()
            
            // Simpler path for faster drawing
            flamePath.reset()
            val h = p.size * 2.5f
            val w = p.size * 0.8f
            
            flamePath.moveTo(p.x - w, p.y)
            flamePath.quadTo(p.x - w * 1.1f, p.y - h * 0.4f, p.x, p.y - h)
            flamePath.quadTo(p.x + w * 1.1f, p.y - h * 0.4f, p.x + w, p.y)
            flamePath.close()
            
            canvas.drawPath(flamePath, particlePaint)
        }

        // Optional: Simplified embers
        particlePaint.color = Color.WHITE
        for (p in particles) {
            if (p.life > 0.85f && random.nextFloat() > 0.96f) {
                canvas.drawRect(p.x, p.y - 15f, p.x + 4f, p.y - 11f, particlePaint)
            }
        }
    }
}
