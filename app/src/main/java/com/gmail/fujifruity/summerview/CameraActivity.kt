package com.gmail.fujifruity.summerview

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.Interpolator
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.VideoCapture
import androidx.camera.core.impl.VideoCaptureConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import com.gmail.fujifruity.summerview.databinding.ActivityCameraBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Suppress("NAME_SHADOWING")
@SuppressLint("RestrictedApi")
class CameraActivity : AppCompatActivity(), VideoCapture.OnVideoSavedCallback {
    companion object {
        private val TAG = CameraActivity::class.java.simpleName
    }

    private val requestCodePermissions = 1
    private val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private val viewModel: CameraViewModel by viewModels {
        CameraViewModelFactory(
            GeoVideoRepository(
                application
            )
        )
    }
    private val binding: ActivityCameraBinding by lazy {
        ActivityCameraBinding.inflate(layoutInflater)
    }
    private var videoCapture: VideoCapture? = null
    private lateinit var pointProvider: PointProvider
    private var orientationEventListener: OrientationEventListener? = null
    private var address: String? = null
    private val prefManager by lazy {
        PreferenceManager.getDefaultSharedPreferences(this@CameraActivity)
    }

    /**
     * when binding video capture or stop recording, this value should be updated so that we can detect display rotation
     */
    private var firstDisplayRotation = 0
    private fun updateFirstDisplayRotation() {
        firstDisplayRotation = windowManager.defaultDisplay.rotation
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        logd(TAG) { "onCreate" }
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN

        setContentView(binding.root)

        if (allPermissionsGranted(permissions)) {
            // on the main thread, we use previewView.post { ... } to make sure that previewView
            // has already been inflated into the view when bind() is called.
            binding.previewView.post { bind() }
        } else {
            ActivityCompat.requestPermissions(this, permissions, requestCodePermissions)
        }

        pointProvider =
            PointProvider(this) { point ->
                logv(TAG) { "update: $point" }
                viewModel.onPointReceive(point)
            }

        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                // videoCapture.setTargetRotation should obey screen orientation
                val rotation =
                    if (orientation in 0..180) Surface.ROTATION_270 else Surface.ROTATION_90
                videoCapture?.setTargetRotation(rotation)
                // we have to cancel out PreviewView's auto-rotation because this activity's
                // screen orientation is locked to userLandscape by manifest
                binding.previewView.rotation =
                    if (windowManager.defaultDisplay.rotation != firstDisplayRotation) 180f else 0f
            }
        }.also { it.enable() }

        binding.recButton.setOnClickListener {
            viewModel.onRecButtonClick()
        }

        viewModel.isRecording.observe(this, Observer { isRecording ->
            if (isRecording) {
                Log.i(TAG, "start recording")
                startRecording()
                Log.i(TAG, "start address lookup intent service")
                pointProvider.startAddressLookup {
                    address =
                        PointProvider.trimAddress(
                            it
                        )
                }
            } else {
                videoCapture?.also {
                    Log.i(TAG, "stop recording")
                    it.stopRecording()
                    updateFirstDisplayRotation()
                }
            }
            // configure `REC` text view
            binding.recText.isVisible = isRecording
            if (isRecording) {
                val blinker = AlphaAnimation(1.0f, 0.0f).apply {
                    duration = 2000
                    interpolator = Interpolator { it.roundToInt().toFloat() }
                    repeatCount = Animation.INFINITE
                }
                binding.recText.startAnimation(blinker)
            } else {
                binding.recText.clearAnimation()
            }
            // configure rec button
            val drawableId = when {
                isRecording -> R.drawable.ic_baseline_stop_24
                else -> R.drawable.ic_baseline_videocam_24
            }
            binding.recButton.setImageDrawable(getDrawable(drawableId))
        })
        viewModel.isRecButtonEnabled.observe(this, Observer { isEnabled ->
            binding.recButton.isEnabled = isEnabled
            binding.recButton.alpha = if (isEnabled) 1.0f else 0.5f
        })
        viewModel.gnssAccuracyMeter.observe(this, Observer { meter ->
            binding.gnssAccuracyView.update(meter)
        })

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
                    binding.previewView.post { bind() }
                } else {
                    Log.i(TAG, "permissions not granted")
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "start location update")
        pointProvider.requestLocationUpdate()
        Log.i(TAG, "start orientation event listener")
        orientationEventListener?.enable()
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "stop location update")
        pointProvider.removeLocationUpdates()
        Log.i(TAG, "stop orientation event listener")
        orientationEventListener?.disable()
    }

    private fun bind() {
        updateFirstDisplayRotation()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val preview = Preview.Builder().apply {
            setTargetName("Preview")
            Camera2Interop.Extender(this).apply {
                setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF
                )
                setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f)
            }
        }.build()
        videoCapture = VideoCaptureConfig.Builder().apply {
            setTargetName("VideoCapture")
            // Use values from preferences
            val defaultFps = resources.getStringArray(R.array.fps_values)[0]
            val fps = prefManager.getString(getString(R.string.pref_fps), defaultFps)!!.toInt()
            setVideoFrameRate(fps)
            val defaultResolution = resources.getStringArray(R.array.resolution_values)[0]
            val resolution =
                prefManager.getString(getString(R.string.pref_resolution), defaultResolution)!!
                    .split("x")
                    .let { (w, h) -> Size(w.toInt(), h.toInt()) }
            setTargetResolution(resolution)
            // e.g. 640x360 ==> 691 kbps
            val bitrate = resolution.let { it.width * it.height * 3 }
            setBitRate(bitrate)
        }.build()

        cameraProviderFuture.addListener(
            Runnable {
                logd(TAG) { "bind use cases" }
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.bindToLifecycle(
                    this@CameraActivity,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    videoCapture
                )
                preview.setSurfaceProvider(binding.previewView.createSurfaceProvider())
            }, ContextCompat.getMainExecutor(this@CameraActivity)
        )

    }

    private fun startRecording() {
        val saveLocation = run {
            val timeStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            // `20191231_115959_XXXtown.geo.mp4` or `20191231_115959.geo.mp4`
            val filename = "$timeStamp${address?.let { "_$it" } ?: ""}.geo.mp4"
            // respect preference value if it is set, otherwise fallbacks to primary external storage
            val prefDirPath =
                prefManager.getString(getString(R.string.pref_file_saving_location), null)
            val saveDir = prefDirPath?.let {
                logd(TAG) { "video saving location preference found" }
                val prefDir = File(prefDirPath)
                if (prefDir.exists()) {
                    prefDir
                } else {
                    logd(TAG) { "... but the location does not exist" }
                    null
                }
            } ?: externalMediaDirs.first()
            File(saveDir, filename)
        }
        videoCapture!!.startRecording(saveLocation, ContextCompat.getMainExecutor(this), this)
    }

    override fun onVideoSaved(file: File) {
        viewModel.onVideoSaved(this, file)
        "video saved: ${file.path}".also {
            Toast.makeText(this@CameraActivity, it, Toast.LENGTH_SHORT).show()
            Log.i(TAG, it)
        }
    }

    override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
        viewModel.onVideoSaved(this, null)
        "Failed to save video. $message".also {
            Toast.makeText(this@CameraActivity, it, Toast.LENGTH_LONG).show()
            Log.e(TAG, it, cause)
        }
    }

}
