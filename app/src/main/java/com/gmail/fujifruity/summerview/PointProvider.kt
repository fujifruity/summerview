package com.gmail.fujifruity.summerview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import android.util.Log
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices

@SuppressLint("MissingPermission")
class PointProvider(val context: Context, val onPointUpdate: (Point) -> Unit) {

    private val locationClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationRequest = LocationRequest.create()?.apply {
        interval = 800
        fastestInterval = 800
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }
    private val locationCallback = object : LocationCallback() {
        var prevPoint: Point? = null
        var prevLocation: Location? = null

        override fun onLocationResult(locationResult: LocationResult?) {
            locationResult ?: return
            val liveLocation = locationResult.locations.first()
            val livePoint = Point.from(
                liveLocation,
                prevLocation,
                prevPoint
            )
            prevPoint = livePoint
            prevLocation = liveLocation
            onPointUpdate(livePoint)
        }
    }

    fun requestLocationUpdate() {
        locationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    fun removeLocationUpdates() {
        locationClient.removeLocationUpdates(locationCallback)
    }

    fun startAddressLookup(onAddressReceive: (rawAddress: String) -> Unit) {
        locationClient.lastLocation.addOnSuccessListener { location ->
            location ?: return@addOnSuccessListener
            if (!Geocoder.isPresent()) return@addOnSuccessListener
            val addressResultReceiver = object : ResultReceiver(Handler()) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    val rawAddress =
                        resultData?.getString(FetchAddressIntentService.RESULT_DATA_KEY)
                    if (rawAddress != null && resultCode == FetchAddressIntentService.SUCCESS_RESULT) {
                        onAddressReceive(rawAddress)
                    } else {
                        Log.e(TAG, "Failed fetching address")
                    }
                }
            }
            val intent = Intent(context, FetchAddressIntentService::class.java).apply {
                putExtra(FetchAddressIntentService.RECEIVER, addressResultReceiver)
                putExtra(FetchAddressIntentService.LOCATION_DATA_EXTRA, location)
            }
            context.startService(intent)
        }
    }

    companion object {
        private val TAG = PointProvider::class.java.simpleName

        fun trimAddress(rawAddress: String) = rawAddress.split(",")[0].split(" ").last()
    }

}