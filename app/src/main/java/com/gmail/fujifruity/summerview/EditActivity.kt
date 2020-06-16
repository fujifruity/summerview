package com.gmail.fujifruity.summerview

import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.animation.AlphaAnimation
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.gmail.fujifruity.summerview.databinding.ActivityEditBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EditActivity : AppCompatActivity() {
    companion object {
        private val TAG = EditActivity::class.java.simpleName
        const val PLAYBACK_POSITION = "PLAYBACK_POSITION"
        const val PLAYBACK_SPEED = "PLAYBACK_SPEED"
    }

    private val viewModel: EditViewModel by viewModels {
        video = intent.getSerializableExtra(MapsActivity.KEY_TARGET_VIDEO) as GeoVideo
        EditViewModelFactory(
            video,
            GeoVideoRepository(application)
        )
    }
    private lateinit var binding: ActivityEditBinding
    private lateinit var player: PlayerWrapper
    private lateinit var video: GeoVideo
    private var lastPlaybackPosition: Long? = null
    private var lastPlaybackSpeed: Double? = null
    private lateinit var progressWatcher: Job

    override fun onCreate(savedInstanceState: Bundle?) {
        logd(TAG) { "onCreate" }
        super.onCreate(savedInstanceState)
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lastPlaybackPosition = savedInstanceState?.getLong(PLAYBACK_POSITION)
        lastPlaybackSpeed = savedInstanceState?.getDouble(PLAYBACK_SPEED)

        binding.surfaceView.holder.addCallback(surfaceHolderCallback)

        binding.playPauseButton.apply {
            setOnClickListener {
                viewModel.onPlayButtonClick(player.isPlaying)
                player.togglePause()
                this.startAnimation(disappearingAnimation)
            }
        }.also { it.startAnimation(disappearingAnimation) }

        viewModel.isPlaying.observe(this, Observer { isPlaying ->
            val resId = if (isPlaying) {
                R.drawable.ic_baseline_play_arrow_24
            } else {
                R.drawable.ic_baseline_pause_24
            }
            binding.playPauseButton.setImageDrawable(getDrawable(resId))
        })

        binding.dottedSeekbar.apply {
            max = video.getDurationMs(this@EditActivity).toInt()
            // When user tap the seek bar, make player seek to the position.
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {}
                override fun onStartTrackingTouch(p0: SeekBar?) {}
                override fun onStopTrackingTouch(seekbar: SeekBar?) {
                    player.seekTo(seekbar!!.progress.toLong())
                }
            })
        }

        viewModel.isAlertAddMode.observe(this, Observer { isAddMode ->
            val resId = if (isAddMode) {
                R.drawable.ic_baseline_add_24
            } else {
                R.drawable.ic_baseline_remove_24
            }
            binding.alertButton.setImageDrawable(getDrawable(resId))
        })

        var alertMsgStore = getString(R.string.warning)
        viewModel.nearbyAlertDesc.observe(this, Observer { nearbyAlertDesc ->
            binding.alertDescView.apply {
                if (nearbyAlertDesc != null) {
                    alertMsgStore = binding.alertDescView.text.toString()
                    this.setText(nearbyAlertDesc)
                    this.isEnabled = false
                } else {
                    this.setText(alertMsgStore)
                    this.isEnabled = true
                }
            }
        })

        binding.alertButton.setOnClickListener {
            viewModel.onAlertButtonClick(player.currentPositionMs, alertMsgStore)
        }

        viewModel.alertPositions.observe(this, Observer {
            binding.dottedSeekbar.dotPositions = it
        })

        viewModel.playerPositionMs.observe(this, Observer { positionMs ->
            binding.dottedSeekbar.progress = positionMs
        })

    }

    override fun onStop() {
        super.onStop()
        logd(TAG) { "restore position and speed" }
        lastPlaybackPosition = player.currentPositionMs
        lastPlaybackSpeed = player.playbackSpeed
        Log.i(TAG, "stop player")
        player.close()
        Log.i(TAG, "stop watching progress")
        progressWatcher.cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        logd(TAG) { "save position and speed" }
        outState.putLong(PLAYBACK_POSITION, player.currentPositionMs)
        outState.putDouble(PLAYBACK_SPEED, player.playbackSpeed)
        super.onSaveInstanceState(outState)
    }

    private val surfaceHolderCallback = object : SurfaceHolder.Callback {
        override fun surfaceDestroyed(p0: SurfaceHolder?) {
            logd(TAG) { "surfaceDestroyed" }
        }

        override fun surfaceCreated(surfaceHolder: SurfaceHolder) {}
        override fun surfaceChanged(surfaceHolder: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
            logd(TAG) { "surfaceChanged" }
            Log.i(TAG, "start player")
            player = VideoPlayerWrapper(
                this@EditActivity,
                surfaceHolder.surface
            )
            // Restore last position and speed.
            player.play(video.file.toUri(), lastPlaybackPosition ?: 0)
            lastPlaybackSpeed?.also {
                player.playbackSpeed = it
            }
            Log.i(TAG, "start watching progress")
            progressWatcher = viewModel.viewModelScope.launch {
                while (true) {
                    delay(100)
                    viewModel.onPlayerProgress(player.currentPositionMs)
                }
            }
        }
    }

    private val disappearingAnimation = AlphaAnimation(1.0f, 0.0f).apply {
        duration = 1700
        fillAfter = true
    }

}
