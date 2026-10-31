package com.example.capsule

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** One reusable, closed-form damped spring. Retargeting preserves the current value and velocity. */
internal class CapsuleSpring {
    var value: Float = 0f
        private set
    var target: Float = 0f
        private set
    var velocity: Float = 0f
        private set

    fun snapTo(position: Float) {
        value = position
        target = position
        velocity = 0f
    }

    fun retarget(position: Float) {
        target = position
    }

    fun setVelocity(pixelsPerSecond: Float) {
        velocity = pixelsPerSecond
    }

    /** Shift both ends of a spring when only its transparent overlay bounds change. */
    fun offsetBy(delta: Float) {
        if (!delta.isFinite()) return
        value += delta
        target += delta
    }

    /** Advance exactly for the elapsed time; the result does not depend on display refresh rate. */
    fun advance(deltaSeconds: Float, responseSeconds: Float, dampingRatio: Float) {
        val t = deltaSeconds.coerceIn(0f, 2f).toDouble()
        if (t <= 0.0) return
        val response = responseSeconds.coerceIn(0.08f, 2.5f).toDouble()
        val zeta = dampingRatio.coerceIn(0.05f, 3f).toDouble()
        val omega = 2.0 * PI / response
        val y0 = (value - target).toDouble()
        val v0 = velocity.toDouble()

        val y: Double
        val v: Double
        when {
            zeta < 1.0 - 1e-5 -> {
                val alpha = zeta * omega
                val beta = omega * sqrt(1.0 - zeta * zeta)
                val b = (v0 + alpha * y0) / beta
                val angle = beta * t
                val c = cos(angle)
                val s = sin(angle)
                val envelope = exp(-alpha * t)
                val shape = y0 * c + b * s
                y = envelope * shape
                v = envelope * (-alpha * shape + beta * (-y0 * s + b * c))
            }
            zeta > 1.0 + 1e-5 -> {
                val root = sqrt(zeta * zeta - 1.0)
                val r1 = -omega * (zeta - root)
                val r2 = -omega * (zeta + root)
                val c1 = (v0 - r2 * y0) / (r1 - r2)
                val c2 = y0 - c1
                val e1 = exp(r1 * t)
                val e2 = exp(r2 * t)
                y = c1 * e1 + c2 * e2
                v = c1 * r1 * e1 + c2 * r2 * e2
            }
            else -> {
                val b = v0 + omega * y0
                val envelope = exp(-omega * t)
                y = (y0 + b * t) * envelope
                v = (b - omega * (y0 + b * t)) * envelope
            }
        }
        value = (target.toDouble() + y).toFloat()
        velocity = v.toFloat()
        if (!value.isFinite() || !velocity.isFinite()) snapTo(target)
    }

    fun isSettled(positionEpsilon: Float, velocityEpsilon: Float): Boolean =
        abs(value - target) <= positionEpsilon && abs(velocity) <= velocityEpsilon
}

/** Width and height are deliberately phased: width leads opening, height leads closing. */
internal data class CapsuleMotionProfile(
    val widthResponse: Float,
    val heightResponse: Float,
    val dampingRatio: Float,
    val gestureResponse: Float = 0.34f
) {
    companion object {
        fun from(speed: String, style: String, expanding: Boolean): CapsuleMotionProfile {
            val base = when (speed) {
                "fast" -> Triple(0.31f, 0.38f, 0.84f)
                "slow" -> Triple(0.60f, 0.70f, 0.84f)
                else -> Triple(0.42f, 0.50f, 0.84f)
            }
            if (style == "settling_dock") return CapsuleMotionProfile(0.52f, 0.58f, 0.96f)
            val (width, height, damping) = base
            return if (expanding) CapsuleMotionProfile(width, height, damping)
            else CapsuleMotionProfile(height, width, damping)
        }
    }
}
