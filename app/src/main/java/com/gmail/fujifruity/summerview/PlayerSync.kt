package com.gmail.fujifruity.summerview

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.core.net.toUri
import com.gmail.fujifruity.videoplayer.VideoPlayer

class PlayerSync(
    private val player: PlayerWrapper,
    private val videos: Set<GeoVideo>,
    private val onSimilarFootprintFound: (Footprint) -> Unit,
    private val onGetOutOfCourse: () -> Unit,
    private val onComeBackToCourse: () -> Unit
) {
    private var isOutOfCourse = true
    var currentVideo: GeoVideo? = null
        private set

    /** Maximum play speed that [player] has to manage. */
    private val maxPlaySpeed = 6.0

    /** An assuming what will location update interval be. */
    private val updateIntervalMs = 1000

    /**
     * Tries to find the similar nearest [Footprint] with all videos.
     * If it is found, set the video to [player] and start playback from the corresponding position.
     */
    private fun findSimilarNearestFootprint(livePoint: Point): Footprint? {
        val videos = currentVideo?.let { videos - it } ?: videos
        val videoToFootprint = videos.asSequence().mapNotNull { video ->
            val nearestFootprint = video.findSimilarNearestFootprint(livePoint)
            nearestFootprint?.let { video to it }
        }.firstOrNull()
        return videoToFootprint?.let { (video, nearestFootprint) ->
            logd(TAG) { "Appropriate video found: ${video.title}; set it to player." }
            player.play(video.file.toUri(), nearestFootprint.positionMs)
            currentVideo = video
            nearestFootprint
        }
    }

    fun synchronizePlayback(livePoint: Point) {
        logd(TAG) { "livePoint=${livePoint}" }
        val nearestFootprint =
            currentVideo?.findSimilarNearestFootprint(livePoint)
                ?: findSimilarNearestFootprint(livePoint)
        if (nearestFootprint == null) {
            if (!isOutOfCourse) {
                Log.i(TAG, "getting out of course")
                isOutOfCourse = true
                onGetOutOfCourse()
                player.playbackSpeed = 0.0
            }
            logd(TAG) { "similar footprint not found, continue" }
            return
        }
        if (isOutOfCourse) {
            Log.i(TAG, "coming back to course")
            isOutOfCourse = false
            onComeBackToCourse()
        }
        onSimilarFootprintFound(nearestFootprint)

        if (!livePoint.isMoving()) {
            val playingFootprint = currentVideo!!.getFootprint(player.currentPositionMs)
            val errorMeter = nearestFootprint.distMeter(playingFootprint)
            if (errorMeter >= livePoint.thresholdMeter()) {
                player.seekTo(nearestFootprint.positionMs)
            }
            Log.i(TAG, "Too little movement, pause.")
            player.playbackSpeed = 0.0
            return
        }

        // Calculate next play speed and set it to player.
        val liveSpeedOverNearest =
            (livePoint.speed / nearestFootprint.point.speed).let { if (it.isNaN()) 0.0 else it }
        val nearestWillAdvanceMs = updateIntervalMs * liveSpeedOverNearest
        val playerDelayMs = nearestFootprint.positionMs - player.currentPositionMs
        val msToAdjust = playerDelayMs + nearestWillAdvanceMs
        val adjustable = if (playerDelayMs > 0) {
            // if player has fallen behind:
            //      playerPos     nearestPos
            // ---------|--------------|-----------------------|---> time
            //          |playerDelay(+)|nearestWillAdvanceMs(+)|
            //          |         msToAdjust(+)                |
            val maxMsPlayerCanAdvance = updateIntervalMs * maxPlaySpeed
            msToAdjust < maxMsPlayerCanAdvance
        } else {
            // if player has exceeded:
            //      nearestPos     playerPos
            // ---------|--------------|-------------|---> time
            //          |   nearestWillAdvanceMs(+)  |
            //          |playerDelay(-)|             |
            //                         |msToAdjust(+)|
            msToAdjust > 0
        }
        val newPlaySpeed = if (adjustable) {
//                   msPlayerShouldAdvance == msToAdjust
//   updateIntervalMs * playSpeedCandidate == msToAdjust
//                      playSpeedCandidate == msToAdjust / updateIntervalMs
            msToAdjust / updateIntervalMs
        } else {
            player.seekTo(nearestFootprint.positionMs)
            liveSpeedOverNearest
        }
        player.playbackSpeed = newPlaySpeed
    }

    companion object {
        private val TAG = PlayerSync::class.java.simpleName
    }

}

abstract class PlayerWrapper {
    abstract val currentPositionMs: Long
    abstract val durationMs: Long
    abstract val isPlaying: Boolean
    abstract var playbackSpeed: Double
    abstract fun play(videoUri: Uri, startingPositionMs: Long = 0)
    abstract fun seekTo(positionMs: Long)
    abstract fun togglePause()
    abstract fun close()
}

class VideoPlayerWrapper(context: Context, surface: Surface) : PlayerWrapper() {
    private val player = VideoPlayer(context, surface)
    override val currentPositionMs: Long
        get() = player.currentPositionMs
    override val isPlaying: Boolean
        get() = player.isPlaying
    override val durationMs: Long
        get() = player.durationMs
    override var playbackSpeed: Double
        get() = player.playbackSpeed
        set(value) {
            player.playbackSpeed = value
        }

    override fun play(videoUri: Uri, startingPositionMs: Long) {
        player.play(videoUri, startingPositionMs, false)
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun togglePause() {
        player.togglePause()
    }

    override fun close() {
        player.close()
    }

}
