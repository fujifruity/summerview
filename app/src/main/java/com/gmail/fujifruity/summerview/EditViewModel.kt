package com.gmail.fujifruity.summerview

import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

class EditViewModelFactory(
    private val video: GeoVideo,
    private val repository: GeoVideoRepository
) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T =
        EditViewModel(video, repository) as T
}

class EditViewModel(
    private val video: GeoVideo,
    private val repository: GeoVideoRepository
) : ViewModel() {
    companion object {
        private val TAG = EditViewModel::class.java.simpleName
    }

    // will be updated when repository is updated, that is when add or remove alerts
    val alertPositions = Transformations.map(repository.videos) {
        video.alerts.map { it.positionMs.toInt() }
    }

    private val _isPlaying = MutableLiveData<Boolean>(true)
    val isPlaying: LiveData<Boolean> get() = _isPlaying

    fun onPlayButtonClick(isPlaying: Boolean) {
        _isPlaying.value = !isPlaying
    }

    private val _playerPositionMs = MutableLiveData<Int>(0)
    val playerPositionMs: LiveData<Int> get() = _playerPositionMs

    private val _isAlertAddMode = MutableLiveData<Boolean>(true)
    val isAlertAddMode: LiveData<Boolean> get() = _isAlertAddMode

    private val _nearbyAlertDesc = MutableLiveData<String?>(null)
    val nearbyAlertDesc: LiveData<String?> get() = _nearbyAlertDesc

    fun onPlayerProgress(currentPositionMs: Long) {
        val positionToleranceMs = 1000
        _playerPositionMs.value = currentPositionMs.toInt()
        val nearbyAlert = video.alerts.filter {
            (it.positionMs - currentPositionMs).absoluteValue < positionToleranceMs
        }.minBy {
            (it.positionMs - currentPositionMs).absoluteValue
        }
        when (Pair(_isAlertAddMode.value, nearbyAlert != null)) {
            true to true -> {
                // enter delete-mode. view controller should store current text input and
                // replace it with the description of nearby alert.
                logd(TAG) { "alert delete-mode" }
                _isAlertAddMode.value = false
                _nearbyAlertDesc.value = nearbyAlert!!.description
            }
            false to false -> {
                // enter add-mode. view controller should restore stored text input.
                logd(TAG) { "alert add-mode" }
                _isAlertAddMode.value = true
                _nearbyAlertDesc.value = null
            }
        }
    }

    fun onAlertButtonClick(currentPositionMs: Long, alertDescription: String) {
        viewModelScope.launch {
            if (_isAlertAddMode.value!!) {
                Log.i(TAG, "add alert")
                video.addAlert(currentPositionMs, alertDescription)
            } else {
                Log.i(TAG, "delete alert")
                video.removeNearestAlert(currentPositionMs)
            }
            repository.add(video)
        }
    }

}