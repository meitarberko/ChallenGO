package com.challengo.app.ui.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

class GlossSweepAnimator(
    private val button: View,
    private val gloss: View,
    private val durationMs: Long = 600L
) {
    private var animator: ObjectAnimator? = null

    fun bind() {
        button.setOnHoverListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER) {
                runSweep()
            }
            false
        }

        button.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                runSweep()
            }
            false
        }
    }

    fun clear() {
        animator?.cancel()
        animator = null
    }

    private fun runSweep() {
        if (button.width == 0) {
            button.post { runSweep() }
            return
        }

        animator?.cancel()
        val travel = button.width + gloss.width
        gloss.visibility = View.VISIBLE
        gloss.translationX = -(gloss.width * 1.4f)

        animator = ObjectAnimator.ofFloat(
            gloss,
            View.TRANSLATION_X,
            -(gloss.width * 1.4f),
            (travel * 1.05f)
        ).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    gloss.visibility = View.VISIBLE
                }

                override fun onAnimationEnd(animation: Animator) {
                    resetGloss()
                }

                override fun onAnimationCancel(animation: Animator) {
                    resetGloss()
                }
            })
            start()
        }
    }

    private fun resetGloss() {
        gloss.visibility = View.INVISIBLE
        gloss.translationX = -gloss.width.toFloat()
    }
}
