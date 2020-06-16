package com.gmail.fujifruity.summerview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MapsViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private val TAG = MapsViewModel::class.java.simpleName
    }

    override fun onCleared() {
        super.onCleared()
        logd(TAG) { "onCleared" }
    }

    private val repository =
        GeoVideoRepository(application)
    val videos = repository.videos

    private val _selectedVideo = MutableLiveData<GeoVideo?>()
    val selectedVideo: LiveData<GeoVideo?> get() = _selectedVideo

    private val _isDeleteButtonVisible = MutableLiveData(false)
    val isDeleteButtonVisible: LiveData<Boolean> get() = _isDeleteButtonVisible

    private val _isDialogVisible = MutableLiveData(false)
    val isDialogVisible: LiveData<Boolean> get() = _isDialogVisible

    private val _isProgressBarVisible = MutableLiveData<Boolean>(true)
    val isProgressBarVisible: LiveData<Boolean> get() = _isProgressBarVisible

    fun onVideoSelect(video: GeoVideo) {
        _selectedVideo.value = video
        _isDeleteButtonVisible.value = true
    }

    fun onVideoUnSelect() {
        _selectedVideo.value = null
        _isDeleteButtonVisible.value = false
    }

    fun onDeleteButtonClick() {
        _isDialogVisible.value = true
    }

    fun onDeleteDialogAction(isOk: Boolean) {
        _isDialogVisible.value = false
        if (isOk) {
            viewModelScope.launch {
                val video = _selectedVideo.value!!
                repository.remove(video)
                video.delete()
            }
            _selectedVideo.value = null
        }
    }

}