package com.example.capsule

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Shared renderer used by the live Settings preview and the actual Circular Wave pill icon. */
internal class CircularWaveChargeRenderer(context: Context) {
    private val density = context.resources.displayMetrics.density.coerceAtLeast(0.1f)
    private val active = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val remaining = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bolt = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val boltPath = Path()
    private val charged = Path()
    private val remainingBounds = RectF()

    fun draw(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        unitPx: Float,
        percent: Int,
        tint: Int,
        waveSpacingDp: Int,
        thicknessDp: Int,
        remainderThicknessDp: Int,
        remainderColor: Int,
        waveHeightPercent: Int,
        innerWaveSpacingPercent: Int,
        edgeSoftnessPercent: Int,
        phase: Float,
        motionEnabled: Boolean,
        referenceRingSizeDp: Int = (unitPx / density).toInt().coerceIn(36, 80)
    ) {
        if (unitPx <= 0f) return
        val progress = percent.coerceIn(0, 100) / 100f
        val referenceUnitPx = referenceRingSizeDp.coerceIn(36, 80) * density
        val displayScale = unitPx / referenceUnitPx
        // Fill more of the icon's view box: the old 0.305 radius made the ring look
        // undersized, especially in the compact pill. Keep enough inset for thick
        // strokes and deep waves so the edge never clips at the largest settings.
        // The sliders describe a canonical ring; scale both tracks with the actual
        // view size so a 22 dp companion retains the same visual weight as the main ring.
        val configuredThickness = thicknessDp.coerceIn(2, 10) * density
        val thickness = min((configuredThickness * displayScale).coerceAtLeast(0.75f), unitPx * 0.18f)
        val configuredRemainderThickness = remainderThicknessDp.coerceIn(1, 6) * density
        val remainderThickness = min((configuredRemainderThickness * displayScale).coerceAtLeast(0.75f), unitPx * 0.18f)
        val waveAmount = waveHeightPercent.coerceIn(2, 22) / 100f
        val softness = edgeSoftnessPercent.coerceIn(0, 100) / 100f
        val largestStroke = max(thickness, remainderThickness)
        val radiusLimit = (unitPx / 2f - largestStroke / 2f - density * 1.5f * displayScale)
            .coerceAtLeast(unitPx * 0.23f)
        val radius = min(unitPx * 0.36f, radiusLimit / (1f + waveAmount))
        // Use the configured full-size ring as the geometry reference. Collapsed and
        // companion rings scale this same contour instead of changing lobe count as
        // their view bounds change.
        val referenceThickness = min(configuredThickness, referenceUnitPx * 0.18f)
        val referenceRemainderThickness = min(configuredRemainderThickness, referenceUnitPx * 0.18f)
        val referenceLargestStroke = max(referenceThickness, referenceRemainderThickness)
        val referenceRadiusLimit = (referenceUnitPx / 2f - referenceLargestStroke / 2f - density * 1.5f)
            .coerceAtLeast(referenceUnitPx * 0.23f)
        val referenceRadius = min(referenceUnitPx * 0.36f, referenceRadiusLimit / (1f + waveAmount))
        val circumferenceDp = (2f * Math.PI.toFloat()) * (referenceRadius / density)
        val spacing = waveSpacingDp.coerceIn(3, 24).toFloat()
        val lobes = (circumferenceDp / spacing + 0.5f).toInt().coerceIn(3, 32)
        val innerSpacing = innerWaveSpacingPercent.coerceIn(0, 70) / 100f * 0.42f
        remainingBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        // Keep the remainder stationary and visually subordinate to the charged contour.
        remaining.color = remainderColor
        remaining.alpha = 255
        remaining.strokeWidth = remainderThickness
        val full = progress >= 0.999f
        if (!full) {
            val chargedSweep = 360f * progress
            canvas.drawArc(remainingBounds, -90f + chargedSweep, 360f - chargedSweep, false, remaining)
        }

        active.color = tint
        active.alpha = 255
        active.strokeWidth = thickness
        if (progress > 0f) {
            charged.reset()
            // Keep enough points for a short endpoint join to remain smooth at compact sizes.
            val sampleCount = max(32, (640f * progress).toInt())
            val phaseOffset = if (motionEnabled) phase.coerceIn(0f, 1f) * (Math.PI * 2.0) else 0.0
            for (i in 0..sampleCount) {
                val alongCharged = i.toFloat() / sampleCount
                val angleFromTop = alongCharged * Math.PI * 2.0 * progress
                val movingWave = shapedWave(angleFromTop + phaseOffset, lobes, innerSpacing, softness)
                // Before full charge, blend the scallops into the fixed remainder only
                // over a small fraction of one lobe at each endpoint. Fading across the
                // whole charged sweep made a visibly smooth dead zone at both ends.
                // At 100%, the whole ring becomes one continuously animated contour.
                val chargedSweep = Math.PI * 2.0 * progress
                val endpointDistance = min(angleFromTop, chargedSweep - angleFromTop).coerceAtLeast(0.0)
                // The circular remainder only needs a tiny blend at each endpoint;
                // a wide fade reads as a missing section of the charging wave.
                val fadeSpan = (Math.PI * 2.0 / lobes) * 0.04
                val joinProgress = (endpointDistance / fadeSpan).coerceIn(0.0, 1.0).toFloat()
                val smoothJoin = joinProgress * joinProgress * (3f - 2f * joinProgress)
                val envelope = if (full) 1f else smoothJoin
                val animatedRadius = radius * (1f + waveAmount * movingWave * envelope)
                val angle = angleFromTop - Math.PI / 2.0
                val x = centerX + kotlin.math.cos(angle).toFloat() * animatedRadius
                val y = centerY + kotlin.math.sin(angle).toFloat() * animatedRadius
                if (i == 0) charged.moveTo(x, y) else charged.lineTo(x, y)
            }
            if (full) charged.close()
            // No traveling white highlight: the charged wave shape itself animates,
            // while the remaining-charge track stays visually anchored.
            canvas.drawPath(charged, active)
        }

        drawBolt(canvas, centerX, centerY, unitPx, tint)
    }

    private fun shapedWave(angleFromTop: Double, lobes: Int, innerSpacing: Float, softness: Float): Float {
        val waveAngle = lobes * angleFromTop
        val baseWave = ((sin(waveAngle) - innerSpacing.toDouble() * sin(waveAngle * 3.0)) /
            (1.0 + innerSpacing)).toFloat()
        // Higher softness blends toward the naturally smooth sine contour.
        val exponent = 0.82f + 0.18f * softness
        return (if (baseWave < 0f) -1f else 1f) *
            abs(baseWave).toDouble().pow(exponent.toDouble()).toFloat()
    }

    private fun drawBolt(canvas: Canvas, centerX: Float, centerY: Float, unitPx: Float, tint: Int) {
        val u = unitPx * 0.22f
        bolt.color = tint
        bolt.alpha = 255
        boltPath.reset()
        boltPath.moveTo(centerX + u * 0.18f, centerY - u)
        boltPath.lineTo(centerX - u * 0.55f, centerY + u * 0.12f)
        boltPath.lineTo(centerX - u * 0.05f, centerY + u * 0.12f)
        boltPath.lineTo(centerX - u * 0.18f, centerY + u)
        boltPath.lineTo(centerX + u * 0.58f, centerY - u * 0.16f)
        boltPath.lineTo(centerX + u * 0.08f, centerY - u * 0.16f)
        boltPath.close()
        canvas.drawPath(boltPath, bolt)
    }

}

/** Live Circular Wave settings preview, rendered through the same renderer as the pill. */
class CircularWaveChargePreviewView(context: Context) : View(context) {
    private val renderer = CircularWaveChargeRenderer(context)
    private var chargePercent = 50
    private var ringSizeDp = 48
    private var waveSpacingDp = 12
    private var thicknessDp = 3
    private var remainderThicknessDp = 2
    private var remainderColor = 0xFF777777.toInt()
    private var cycleDurationMs = 6_980L
    private var waveHeightPercent = 9
    private var innerWaveSpacingPercent = 28
    private var edgeSoftnessPercent = 55
    private var tint = 0xFFFFFFFF.toInt()
    private var motionEnabled = true
    private var phase = 0f

    private val motionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }
    init {
        contentDescription = "Circular Wave preview at 50 percent, expanded and collapsed sizes"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setPreviewSurface(top: Int, bottom: Int, outline: Int) {
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(top, bottom)
        ).apply {
            cornerRadius = 22f * resources.displayMetrics.density
            setStroke((1f * resources.displayMetrics.density).toInt().coerceAtLeast(1), outline)
        }
    }

    fun updateSettings(
        ringSizeDp: Int,
        waveSpacingDp: Int,
        thicknessDp: Int,
        remainderThicknessDp: Int,
        remainderColor: Int,
        cycleDurationMs: Long,
        waveHeightPercent: Int,
        innerWaveSpacingPercent: Int,
        edgeSoftnessPercent: Int,
        tint: Int,
        motionEnabled: Boolean
    ) {
        val previousDuration = this.cycleDurationMs
        this.ringSizeDp = ringSizeDp.coerceIn(36, 80)
        this.waveSpacingDp = waveSpacingDp.coerceIn(3, 24)
        this.thicknessDp = thicknessDp.coerceIn(2, 10)
        this.remainderThicknessDp = remainderThicknessDp.coerceIn(1, 6)
        this.remainderColor = remainderColor
        this.cycleDurationMs = cycleDurationMs.coerceIn(580L, 24_000L)
        this.waveHeightPercent = waveHeightPercent.coerceIn(2, 22)
        this.innerWaveSpacingPercent = innerWaveSpacingPercent.coerceIn(0, 70)
        this.edgeSoftnessPercent = edgeSoftnessPercent.coerceIn(0, 100)
        this.tint = tint
        this.motionEnabled = motionEnabled

        if (previousDuration != this.cycleDurationMs) {
            val currentPhase = phase
            motionAnimator.duration = this.cycleDurationMs
            if (motionAnimator.isStarted) {
                motionAnimator.cancel()
                motionAnimator.start()
                motionAnimator.currentPlayTime = (currentPhase * motionAnimator.duration).toLong()
            }
        }
        updateMotion()
        invalidate()
    }

    fun setPreviewTint(color: Int) {
        tint = color
        invalidate()
    }

    fun setPreviewChargePercent(percent: Int) {
        chargePercent = percent.coerceIn(0, 100)
        contentDescription = "Circular Wave preview at $chargePercent percent, expanded and collapsed sizes"
        updateMotion()
        invalidate()
    }

    private fun updateMotion() {
        // At 100%, the gap closes and the full wavy contour keeps moving.
        val shouldAnimate = motionEnabled && isAttachedToWindow && isShown &&
            windowVisibility == View.VISIBLE
        if (shouldAnimate) {
            if (!motionAnimator.isStarted) {
                motionAnimator.duration = cycleDurationMs
                motionAnimator.start()
            }
        } else {
            motionAnimator.cancel()
            if (!motionEnabled) {
                phase = 0f
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateMotion()
    }

    override fun onDetachedFromWindow() {
        motionAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateMotion()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateMotion()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateMotion()
        val density = resources.displayMetrics.density
        val expandedSize = ringSizeDp * density
        val compactSize = (ringSizeDp * 0.72f).toInt().coerceIn(26, 46) * density
        val centerY = height / 2f
        renderer.draw(
            canvas, width * 0.28f, centerY, expandedSize, chargePercent, tint,
            waveSpacingDp, thicknessDp, remainderThicknessDp, remainderColor,
            waveHeightPercent, innerWaveSpacingPercent, edgeSoftnessPercent, phase,
            motionEnabled, ringSizeDp
        )
        renderer.draw(
            canvas, width * 0.72f, centerY, compactSize, chargePercent, tint,
            waveSpacingDp, thicknessDp, remainderThicknessDp, remainderColor,
            waveHeightPercent, innerWaveSpacingPercent, edgeSoftnessPercent, phase,
            motionEnabled, ringSizeDp
        )
    }
}
