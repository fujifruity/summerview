package com.gmail.fujifruity.summerview

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import java.io.File

class CameraViewModelFactory(
    private val repository: GeoVideoRepository
) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T =
        CameraViewModel(repository) as T
}

class CameraViewModel(
    private val repository: GeoVideoRepository
) : ViewModel() {
    companion object {
        private val TAG = CameraViewModel::class.java.simpleName
    }

    private var recordingStartErt: Long? = null
    private val points = mutableListOf<Point>()

    private val _isRecording = MutableLiveData<Boolean>(false)
    val isRecording: LiveData<Boolean> get() = _isRecording

    private val _gnssAccuracyMeter = MutableLiveData(0.0)
    val gnssAccuracyMeter: LiveData<Double> get() = _gnssAccuracyMeter

    private val _isRecButtonEnabled = MutableLiveData<Boolean>(true)
    val isRecButtonEnabled: LiveData<Boolean> get() = _isRecButtonEnabled

    fun onPointReceive(point: Point) {
        _gnssAccuracyMeter.value = point.accuracy
        points.add(point)
        if (points.size == 1) {
            // Re-enable recButton since there are enough points to create GeoVideo.
            _isRecButtonEnabled.value = true
        }
    }

    fun onRecButtonClick() {
        if (_isRecording.value!!) {
            // stop recording
            onRecordingStop()
        } else {
            // start recording
            points.clear()
            _isRecording.value = true
            _isRecButtonEnabled.value = false
            recordingStartErt = SystemClock.elapsedRealtime()
        }
    }

    fun onRecordingStop() {
        _isRecording.value = false
        // disable rec-button until the recording is saved
        _isRecButtonEnabled.value = false
    }

    /**
     * @param file if an error occurred, pass `null`
     */
    fun onVideoSaved(context: Context, file: File?) {
        _isRecButtonEnabled.value = true
        file ?: return
        viewModelScope.launch {
            val video =
                GeoVideo.geovideonize(
                    context,
                    file,
                    points,
                    recordingStartErt!!
                )
            repository.add(video)
        }
    }

}
