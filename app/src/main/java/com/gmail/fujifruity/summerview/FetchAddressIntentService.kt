package com.gmail.fujifruity.summerview

import android.app.IntentService
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.ResultReceiver
import android.util.Log
import java.io.IOException
import java.util.*

/**
 * Perform the background task which fetches the address for given [Location].
 */
class FetchAddressIntentService : IntentService("FetchAddressIntentService") {

    private var receiver: ResultReceiver? = null

    override fun onHandleIntent(intent: Intent?) {
        intent ?: return

        // Get the location passed to this service through an extra.
        val location: Location = intent.getParcelableExtra(LOCATION_DATA_EXTRA)!!
        receiver = intent.getParcelableExtra(RECEIVER)!!

        var addresses: List<Address> = emptyList()
        val geocoder = Geocoder(this, Locale.getDefault())

        try {
            // In this sample, we get just a single address.
            addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
        } catch (ioException: IOException) {
            Log.e(TAG, "Network is not available.", ioException)
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e(
                TAG, "Illegal argument: Lat=${location.latitude} , " +
                        "Lon=${location.longitude}", illegalArgumentException
            )
        }

        // Handle case where no address was found.
        if (addresses.isEmpty()) {
            Log.e(TAG, "addresses.isEmpty()")
            deliverResultToReceiver(FAILURE_RESULT, "no address found")
        } else {
            // Fetch the address lines using getAddressLine, join them, and send them to the thread.
            val address = addresses[0]
            val addressFragments = with(address) {
                (0..maxAddressLineIndex).map { getAddressLine(it) }
            }
            val addressStr = addressFragments.joinToString(separator = "\n")
            logd(TAG) { "found: $addressStr" }
            deliverResultToReceiver(SUCCESS_RESULT, addressStr)
        }
    }

    private fun deliverResultToReceiver(resultCode: Int, message: String) {
        val bundle = Bundle().apply { putString(RESULT_DATA_KEY, message) }
        receiver?.send(resultCode, bundle)
    }


    companion object {
        private val TAG = FetchAddressIntentService::class.java.simpleName
        val SUCCESS_RESULT = 0
        val FAILURE_RESULT = 1
        val RECEIVER = "$TAG.RECEIVER"
        val RESULT_DATA_KEY = "$TAG.RESULT_DATA_KEY"
        val LOCATION_DATA_EXTRA = "$TAG.LOCATION_DATA_EXTRA"
    }

}
