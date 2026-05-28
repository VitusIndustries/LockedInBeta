package com.streakapp.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.random.Random

class ConfettiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val particles = mutableListOf<ConfettiParticle>()
    private val settledParticles = mutableListOf<ConfettiParticle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random
    private var isFadingOut = false
    private var fadeAlpha = 255

    private class ConfettiParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var rotation: Float,
        var vr: Float,
        val color: Int,
        val size: Float
    )

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = Long.MAX_VALUE
        interpolator = LinearInterpolator()
        addUpdateListener {
            update()
            invalidate()
        }
    }

    fun burst() {
        isFadingOut = false
        fadeAlpha = 255
        particles.clear()
        settledParticles.clear()
        
        val colors = intArrayOf(
            0xFFF44336.toInt(), 0xFF2196F3.toInt(), 0xFF4CAF50.toInt(), 
            0xFFFFEB3B.toInt(), 0xFFE91E63.toInt(), 0xFFFF9800.toInt(),
            0xFF9C27B0.toInt(), 0xFF00BCD4.toInt()
        )
        repeat(250) { // Increased from 80 to 250 for high energy
            particles.add(ConfettiParticle(
                x = width / 2f + (random.nextFloat() - 0.5f) * width,
                y = height * -0.1f, // Start slightly off screen top
                vx = (random.nextFloat() - 0.5f) * 40f,
                vy = random.nextFloat() * 20f + 5f,
                rotation = random.nextFloat() * 360f,
                vr = (random.nextFloat() - 0.5f) * 20f,
                color = colors.random(),
                size = 10f + random.nextFloat() * 15f
            ))
        }
        if (!animator.isRunning) animator.start()
        
        postDelayed({ isFadingOut = true }, 5000) // Longer settled time
    }

    private fun update() {
        val gravity = 0.4f
        val friction = 0.98f
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += gravity
            p.vx *= friction
            p.rotation += p.vr
            
            if (p.y >= height - 5f) {
                p.y = height - random.nextFloat() * 10f
                settledParticles.add(p)
                it.remove()
            }
        }
        
        if (isFadingOut) {
            fadeAlpha -= 8
            if (fadeAlpha <= 0) {
                fadeAlpha = 0
                particles.clear()
                settledParticles.clear()
                animator.cancel()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (fadeAlpha <= 0) return
        
        for (p in particles) {
            drawConfetti(canvas, p)
        }
        for (p in settledParticles) {
            drawConfetti(canvas, p)
        }
    }

    private fun drawConfetti(canvas: Canvas, p: ConfettiParticle) {
        paint.color = p.color
        paint.alpha = fadeAlpha
        canvas.save()
        canvas.translate(p.x, p.y)
        canvas.rotate(p.rotation)
        canvas.drawRect(-p.size / 2, -p.size / 4, p.size / 2, p.size / 4, paint)
        canvas.restore()
    }
}
