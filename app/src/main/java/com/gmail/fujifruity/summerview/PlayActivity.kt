@file:Suppress("NAME_SHADOWING")

package com.gmail.fujifruity.summerview

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.gmail.fujifruity.summerview.databinding.ActivityPlayBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PlayActivity : AppCompatActivity() {
    companion object {
        private val TAG = PlayActivity::class.java.simpleName
    }

    private val requestCodePermissions = 1
    private val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private lateinit var binding: ActivityPlayBinding
    private var player: PlayerWrapper? = null
    private var playerSync: PlayerSync? = null
    private var alertFrameLoop: Job? = null
    private val pointProvider by lazy {
        PointProvider(this) { point ->
            binding.gnssAccuracyView.update(point.accuracy)
            playerSync?.synchronizePlayback(point)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        logd(TAG) { "onCreate" }
        super.onCreate(savedInstanceState)
        binding = ActivityPlayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (allPermissionsGranted(permissions)) {
            start()
        } else {
            ActivityCompat.requestPermissions(this, permissions, requestCodePermissions)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            requestCodePermissions -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    start()
                } else {
                    Log.i(TAG, "permissions not granted")
                    finish()
                }
            }
        }
    }

    private fun start() {
        Log.i(TAG, "start player")
        player = VideoPlayerWrapper(
            this,
            binding.surfaceView.holder.surface
        )
        Log.i(TAG, "start location update")
        pointProvider.requestLocationUpdate()
        Log.i(TAG, "start alert frame loop")
        alertFrameLoop = CoroutineScope(Dispatchers.Main).launch {
            binding.alertFrame.alertFrameLoop(
                player!!,
                { playerSync?.currentVideo?.alerts }
            )
        }
        CoroutineScope(Dispatchers.Main).launch {
            val videos = GeoVideoRepository(
                application
            ).getVideos()
            if (videos.isEmpty()) {
                Toast.makeText(this@PlayActivity, "No recordings", Toast.LENGTH_LONG).show()
                return@launch finish()
            }
            playerSync = PlayerSync(
                player!!,
                videos,
                { },
                { binding.outOfCourseTextView.isVisible = true },
                { binding.outOfCourseTextView.isVisible = false }
            )
            binding.progressBar.isVisible = false
        }
    }

    override fun onStart() {
        logd(TAG) { "onStart" }
        super.onStart()
        if (allPermissionsGranted(permissions)) {
            start()
        }
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "stop player")
        player?.close()
        Log.i(TAG, "stop location update")
        pointProvider.removeLocationUpdates()
        Log.i(TAG, "stop alert frame loop")
        alertFrameLoop?.cancel()
    }

}
