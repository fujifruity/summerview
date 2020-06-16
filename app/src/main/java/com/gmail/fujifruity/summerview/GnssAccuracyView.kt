package com.gmail.fujifruity.summerview

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible

/**
 * Shows GNSS intensity according to [Point.accuracy].
 */
class GnssAccuracyView : ConstraintLayout {

    val accuracyMeter: TextView
    private val satelliteIcon: ImageView
    private val signalBars: List<TextView>

    init {
        View.inflate(
            context,
            R.layout.gnss_accuracy_view, this
        )
        accuracyMeter = findViewById(R.id.accuracy_meter)
        accuracyMeter.isVisible = false
        satelliteIcon = findViewById(R.id.peg_icon)
        signalBars = listOf(
            findViewById(R.id.signal_bar_1),
            findViewById(R.id.signal_bar_2),
            findViewById(R.id.signal_bar_3),
            findViewById(R.id.signal_bar_4)
        )
    }

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(context)
    }

    private fun init(context: Context) {
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    fun update(accuracyMeter: Double) {
        // set color to views according to the accuracy value
        val colorId = if (accuracyMeter < 10) {
            android.R.color.holo_green_light
        } else {
            android.R.color.holo_red_light
        }
        val color = ContextCompat.getColor(context, colorId)
        this.accuracyMeter.apply {
            text = "r=%.1fm".format(accuracyMeter)
            setTextColor(color)
        }
        satelliteIcon.imageTintList = ContextCompat.getColorStateList(context, colorId)
        val nBars = when {
            accuracyMeter < 5 -> 4
            accuracyMeter < 10 -> 3
            accuracyMeter < 15 -> 2
            else -> 1
        }
        val opaqueColor = (color - 0x99000000).toInt()
        signalBars.forEachIndexed { index, bar ->
            val color = if (index < nBars) color else opaqueColor
            bar.setBackgroundColor(color)
        }
        // disappearing slowly
        val animDisappearing = AlphaAnimation(1.0f, 0.2f).apply {
            duration = 4000
            fillAfter = true
        }
        this.startAnimation(animDisappearing)
    }

    companion object {
        private val TAG = GnssAccuracyView::class.java.simpleName
    }
}
