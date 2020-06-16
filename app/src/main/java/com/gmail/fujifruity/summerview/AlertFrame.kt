package com.gmail.fujifruity.summerview

import android.content.Context
import android.graphics.drawable.TransitionDrawable
import android.os.Vibrator
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.coroutines.delay

/**
 * A blinking vivid frame
 */
class AlertFrame : ConstraintLayout {

    private var frameImageView: ImageView

    init {
        View.inflate(context, R.layout.alert_frame, this)
        frameImageView = findViewById(R.id.alertFrame_imageView)
    }

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {}

    /**
     * Start polling [player]'s current position. When there's any alert approaching,
     * show blinking frame and alert's description.
     */
    @MainThread
    suspend fun alertFrameLoop(
        player: PlayerWrapper,
        alerts: () -> List<GeoVideo.Alert>?
    ) {
        val startBlinkingBeforeMs = 4000
        var lastAlert: GeoVideo.Alert? = null
        while (true) {
            delay(200)
            val alerts = alerts() ?: return
            val approachingAlert = alerts
                .binarySearchBy(player.currentPositionMs) { it.positionMs }
                .let { idx ->
                    val insertionIdx = if (idx < 0) -idx - 1 else idx
                    alerts.getOrNull(insertionIdx)
                }
            approachingAlert?.also { alert ->
                val dist = alert.positionMs - player.currentPositionMs
                val msPerSec = dist / player.playbackSpeed
                if ((lastAlert == null || alert != lastAlert) && msPerSec < startBlinkingBeforeMs) {
                    logd(TAG) { "show alert" }
                    Toast.makeText(context, alert.description, Toast.LENGTH_LONG).show()
                    logd(TAG) { "blink frame" }
                    blinkFrame()
                    lastAlert = alert
                }
            }
        }
    }


    private fun blinkFrame() {
        val alertFrame = frameImageView.drawable as TransitionDrawable
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        fun startAndReverse(times: Int) {
            if (times > 0) {
                val startMillis = 200
                val endMillis = 800
                handler.post {
                    alertFrame.startTransition(startMillis)
                    vibrator.vibrate(300)
                }
                handler.postDelayed({
                    alertFrame.reverseTransition(endMillis)
                }, startMillis.toLong())
                handler.postDelayed({
                    startAndReverse(times - 1)
                }, startMillis + endMillis.toLong())
            }
        }

        startAndReverse(3)
    }

    companion object {
        private val TAG = AlertFrame::class.java.simpleName
    }
}
