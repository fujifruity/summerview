package com.gmail.fujifruity.summerview

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import com.gmail.fujifruity.summerview.databinding.ActivityMapsBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        private val TAG = MapsActivity::class.java.simpleName
        val KEY_TARGET_VIDEO = "KEY_TARGET_VIDEO"
        val CAMERA_POSITION = "CAMERA_POSITION"
    }

    private val requestCodePermissions = 1
    private val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private lateinit var binding: ActivityMapsBinding
    private val viewModel: MapsViewModel by viewModels()
    private var googleMap: GoogleMap? = null
    private var previousCameraPosition: CameraPosition? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logd(TAG) { "onCreate" }
        previousCameraPosition = savedInstanceState?.getParcelable(CAMERA_POSITION)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        // Register the callback.
        mapFragment.getMapAsync(this)

        viewModel.isDeleteButtonVisible.observe(this, Observer { isVisible ->
            binding.deleteButton.apply {
                this.isVisible = isVisible
                if (isVisible) {
                    setOnClickListener {
                        viewModel.onDeleteButtonClick()
                    }
                    animate().translationX(0f).alpha(1f).setDuration(100)
                } else {
                    alpha = 0f
                    translationX = 100f
                }
            }
        })

        viewModel.isDialogVisible.observe(this, Observer { isVisible ->
            if (!isVisible) return@Observer
            // TODO: rotate device while dialog is shown
            VideoDeleteDialog(
                viewModel.selectedVideo.value!!.title,
                viewModel::onDeleteDialogAction
            ).show(
                supportFragmentManager,
                TAG
            )
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        googleMap?.also {
            logd(TAG) { "save camera position" }
            outState.putParcelable(CAMERA_POSITION, it.cameraPosition)
        }
        super.onSaveInstanceState(outState)
    }

    data class Trail(
        val video: GeoVideo,
        val polyline: Polyline,
        val startMarker: Marker,
        var alertMarkers: List<Marker>
    )

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        logd(TAG) { "onMapReady" }
        this.googleMap = googleMap
        // show current location marker
        googleMap.isMyLocationEnabled = true

        val trails = mutableListOf<Trail>()

        viewModel.videos.observe(this, Observer { videos ->
            videos ?: return@Observer
            if (videos.isEmpty()) {
                val msg = "No recordings"
                Toast.makeText(this@MapsActivity, msg, Toast.LENGTH_LONG).show()
                Log.i(TAG, msg)
                return@Observer finish()
            }
            if (trails.isNotEmpty()) {
                logd(TAG) { "erase trails" }
                trails.forEach { trail ->
                    trail.apply {
                        polyline.remove()
                        alertMarkers.forEach { it.remove() }
                        startMarker.remove()
                    }
                }
                trails.clear()
            }
            logd(TAG) { "draw trails" }
            val boundsBuilder = LatLngBounds.builder()
            videos.forEach { video ->
                val polyline = googleMap.addPolyline(
                    PolylineOptions().apply {
                        val points = video.footprints.map { it.latLng }
                        addAll(points)
                        clickable(true)
                    })
                initPolyline(polyline)
                val alertMarkers = video.alerts.map { addMarker(it) }
                val startMarker = googleMap.addMarker(
                    MarkerOptions().apply {
                        val startPoint = video.footprints.first().latLng
                        position(startPoint)
                        title("Start of ${video.title}")
                        snippet("Tap here to edit alert")
                    })
                trails.add(
                    Trail(
                        video,
                        polyline,
                        startMarker,
                        alertMarkers
                    )
                )
                // Configure zoom level to show this polyline at least beginning and end.
                boundsBuilder.include(video.footprints.first().latLng)
                boundsBuilder.include(video.footprints.last().latLng)
            }

            val cameraUpdate = previousCameraPosition?.let {
                logd(TAG) { "restore camera position" }
                CameraUpdateFactory.newCameraPosition(it)
            } ?: run {
                logd(TAG) { "zoom to show all polylines" }
                val bound = boundsBuilder.build()
                val width = resources.displayMetrics.widthPixels
                val height = resources.displayMetrics.heightPixels
                // offset from edges of the map 12% of screen
                val padding = (width * 0.12).toInt()
                CameraUpdateFactory.newLatLngBounds(bound, width, height, padding)
            }
            googleMap.moveCamera(cameraUpdate)

            // set click listeners
            // When a marker is clicked, fire EditActivity with related video.
            googleMap.setOnInfoWindowClickListener { marker ->
                val targetVideo = trails.first { it.startMarker == marker }.video
                Log.i(TAG, "fire edit activity with ${targetVideo.title}")
                val editIntent = Intent(this@MapsActivity, EditActivity::class.java)
                editIntent.putExtra(KEY_TARGET_VIDEO, targetVideo)
                startActivity(editIntent)
            }
            // When a marker is clicked, change the color of related polyline.
            googleMap.setOnMarkerClickListener { marker ->
                val video =
                    trails.first { it.startMarker == marker || it.alertMarkers.contains(marker) }.video
                viewModel.onVideoSelect(video)
                true
            }
            // When a polyline is clicked, open related marker's info window.
            googleMap.setOnPolylineClickListener { polyline ->
                val video = trails.first { it.polyline == polyline }.video
                viewModel.onVideoSelect(video)
            }
            // When a info window is closed, reset the style of related polyline
            googleMap.setOnInfoWindowCloseListener { marker ->
                viewModel.onVideoUnSelect()
                val polyline = trails.first { it.startMarker == marker }.polyline
                initPolyline(polyline)
                // put the polyline bottom so that another overlapped polylines can be selected
                val minZ = trails.map { it.polyline.zIndex }.min()?.let { it - 1 } ?: 0f
                polyline.zIndex = minZ
            }
        })

        viewModel.selectedVideo.observe(this, Observer { video ->
            video ?: return@Observer
            val trail = trails.first { it.video == video }
            // highlight the polyline and moves it to front
            trail.polyline.apply {
                color = getArgb(android.R.color.holo_blue_dark)
                width = 11f
                zIndex = 1000f
            }
            // show info window
            trail.startMarker.showInfoWindow()
        })

        binding.progressBar.isVisible = false
    }

    private fun getArgb(resourceId: Int) =
        ContextCompat.getColor(this, resourceId)

    private fun addMarker(alert: GeoVideo.Alert): Marker {
        return googleMap!!.addMarker(
            MarkerOptions().apply {
                position(alert.point.latLng)
                icon(alertMarkerIcon)
                anchor(0.5f, 0.5f)
            })
    }

    private fun initPolyline(polyline: Polyline) {
        polyline.apply {
            color = getArgb(android.R.color.holo_blue_light) - 0x66000000
            width = 9f
        }
    }

    private val alertMarkerIcon: BitmapDescriptor by lazy {
        val width = 17
        val height = width
        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val circle = GradientDrawable().apply {
            setColor(getArgb(android.R.color.holo_red_light))
            setStroke(2, Color.WHITE)
            shape = GradientDrawable.OVAL
            setBounds(0, 0, width, height)
        }
        circle.draw(canvas)
        BitmapDescriptorFactory.fromBitmap(
            bitmap
        )
    }

}


class VideoDeleteDialog(
    private val videoTitle: String,
    val onButtonClick: (isOk: Boolean) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder(it).apply {
                setMessage("Delete this recording?\n$videoTitle")
                setPositiveButton("Delete") { dialog, id ->
                    onButtonClick(true)
                }
                setNegativeButton("Cancel") { dialog, id ->
                    onButtonClick(false)
                }
            }.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

}

