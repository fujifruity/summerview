package com.gmail.fujifruity.summerview

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        supportFragmentManager
            .beginTransaction()
            .replace(
                R.id.settings,
                SettingsFragment()
            )
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private val TAG = this::class.java.simpleName

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            // create a list preference from available external media directories
            val fileLocationPref: ListPreference =
                findPreference(getString(R.string.pref_file_saving_location))!!
            val extDirs = requireActivity().externalMediaDirs.map { it.toString() }
            val sdCardTexts = extDirs.mapIndexed { index, s -> "SD card $index" }.drop(1)
            val storageTexts = listOf("Internal storage").plus(sdCardTexts).toTypedArray()
            fileLocationPref.entryValues = extDirs.toTypedArray()
            fileLocationPref.entries = storageTexts

            // find all available wide resolutions
            val wideSizes = run {
                val manager =
                    requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = manager.cameraIdList[0]
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val streamConfigurationMap =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                val sizes = streamConfigurationMap.getOutputSizes(SurfaceTexture::class.java)
                sizes.filter { size -> size.width > size.height * 1.5 }
                    .map { it.toString() }
                    .toTypedArray()
            }
            logd(TAG) { "available screen sizes=\n${wideSizes.joinToString("n")}" }
            val resolutionPref: ListPreference =
                findPreference(getString(R.string.pref_resolution))!!
            resolutionPref.entries = wideSizes
            resolutionPref.entryValues = wideSizes
        }
    }
}