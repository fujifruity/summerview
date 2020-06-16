package com.gmail.fujifruity.summerview

import android.content.pm.PackageManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

fun AppCompatActivity.allPermissionsGranted(permissions: Array<out String>) =
    permissions.all {
        ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

fun logv(tag: String, lazyMsg: () -> String) {
    if (BuildConfig.DEBUG) Log.v(tag, lazyMsg())
}

fun logd(tag: String, lazyMsg: () -> String) {
    if (BuildConfig.DEBUG) Log.d(tag, lazyMsg())
}

