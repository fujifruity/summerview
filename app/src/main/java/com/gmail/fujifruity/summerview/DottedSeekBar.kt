package com.gmail.fujifruity.summerview

/*
 Respects sandrstar's answer: https://stackoverflow.com/questions/17637925/android-seek-bar-customization
 */

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat

/**
 * Seek bar with dots on it on specific time / percent
 */
class DottedSeekBar : AppCompatSeekBar {

    var dotPositions: List<Int>? = null
        set(value) {
            field = value
            invalidate()
        }

    val dotPaint = Paint().apply {
        color = ContextCompat.getColor(context, android.R.color.holo_red_light)
    }

    constructor(context: Context?) : super(context) {
        init(null)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(attrs)
    }

    /**
     * Initializes Seek bar extended attributes from xml
     *
     * @param attributeSet [AttributeSet]
     */
    private fun init(attributeSet: AttributeSet?) {
        val attrsArray =
            context.obtainStyledAttributes(
                attributeSet,
                R.styleable.DottedSeekBar, 0, 0
            )
        val dotsArrayResource =
            attrsArray.getResourceId(R.styleable.DottedSeekBar_dots_positions, 0)
        if (0 != dotsArrayResource) {
            dotPositions = resources.getIntArray(dotsArrayResource).toList()
        }
    }

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw dots
        dotPositions?.also {
            val dotY = measuredHeight / 2f
            val dotRadius = 8f
            val offsetX = dotY
            for (position in it) {
                canvas.drawCircle(
                    offsetX + (position.toFloat() / max) * (measuredWidth - 2 * offsetX),
                    dotY,
                    dotRadius,
                    dotPaint
                )
            }
        }
    }

    companion object {
        private val TAG = DottedSeekBar::class.java.simpleName
    }

}
