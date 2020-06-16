@file:Suppress("NAME_SHADOWING")

package com.gmail.fujifruity.summerview

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.gmail.fujifruity.summerview.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }

    private val requestCodePermissions = 1
    private val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        logd(TAG) { "onCreate" }
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        when {
            allPermissionsGranted(permissions) -> {
                updateRepository()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0]) -> {
                showRationaleDialog()
            }
            else -> {
                ActivityCompat.requestPermissions(this, permissions, requestCodePermissions)
            }
        }
    }


    private fun updateRepository() {
        GlobalScope.launch(Dispatchers.Main) {
            val repository = GeoVideoRepository(application)
            repository.update()
            logd(TAG) { repository.toString() }
            hideProgress()
        }
    }

    private fun hideProgress() {
        binding.progressBar.isVisible = false
        binding.progressTextView.isVisible = false
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
                    Log.i(TAG, "permissions granted")
                    updateRepository()
                } else {
                    Log.i(TAG, "permissions not granted")
                    hideProgress()
                }
            }
        }
    }

    private fun showRationaleDialog() {
        val appName = getString(R.string.app_name)
        AlertDialog.Builder(this)
            .setTitle("$appName needs permission to read from storage")
            .setMessage("$appName needs storage access to find recordings.")
            .setPositiveButton("Ok") { dialog, which ->
                ActivityCompat.requestPermissions(this, permissions, requestCodePermissions)
            }
            .create()
            .show()
    }

    fun fireVideoCapture(view: View) = fireActivity(CameraActivity::class.java)

    fun fireMapsActivity(view: View) = fireActivity(MapsActivity::class.java)

    fun firePlayActivity(view: View) = fireActivity(PlayActivity::class.java)

    fun fireSettingsActivity(view: View) = fireActivity(SettingsActivity::class.java)

    private fun fireActivity(cls: Class<*>) {
        Log.i(TAG, "launch ${cls.name}")
        val intent = Intent(this, cls)
        startActivity(intent)
    }
}

