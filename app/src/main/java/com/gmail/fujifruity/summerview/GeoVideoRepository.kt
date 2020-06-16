package com.gmail.fujifruity.summerview

import android.app.Application
import android.content.Context
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * A cache storing serialized [GeoVideo]s.
 */
@MainThread
class GeoVideoRepository(application: Application) {
    init {
        appContext = application.applicationContext
        file = File(
            appContext.cacheDir, "geovideos"
        )
    }

    val videos: LiveData<Set<GeoVideo>?> get() = _videos

    /**
     * If the video is already cached, it updates the old cache.
     */
    suspend fun add(video: GeoVideo) {
        _videos.value = (getVideos() - video) + video
        writeToFile()
    }

    suspend fun remove(video: GeoVideo) {
        _videos.value = getVideos() - video
        writeToFile()
    }

    /**
     * Updates cache by finding all unknown [GeoVideo]s.
     */
    suspend fun update() {
        logd(TAG) { "update" }
        val allVideoFiles = findAllGeoVideoFiles()
        // remove dangling videos
        val cachedVideos = getVideos()
        val cachedFiles = cachedVideos.map { it.file }
        val danglingFiles = cachedFiles - allVideoFiles
        val existingVideos = cachedVideos.filter { it.file !in danglingFiles }.toSet()
        // add unknown videos
        val unknownFiles = allVideoFiles - cachedFiles
        val unknownVideos = unknownFiles.mapNotNull {
            GeoVideo.fromFileOrNull(it)
        }
        if (danglingFiles.isEmpty() && unknownVideos.isEmpty()) {
            logd(TAG) { "nothing has changed" }
            return
        }
        logd(TAG) { "dangling ${danglingFiles.size} files=${danglingFiles}" }
        logd(TAG) { "unknown ${unknownVideos.size} videos=${unknownVideos.map { it.title }}" }
        _videos.value = existingVideos + unknownVideos
        writeToFile()
    }

    suspend fun forceUpdate() {
        val allVideoUris =
            findAllGeoVideoFiles()
        _videos.value = allVideoUris.mapNotNull {
            GeoVideo.fromFileOrNull(it)
        }.toSet()
        writeToFile()
    }

    fun getVideosBlocking() = runBlocking { getVideos() }
    suspend fun getVideos() =
        Companion.getVideos()

    override fun toString(): String {
        return "${GeoVideoRepository::class.java.simpleName}(\n" +
                videos.value!!.joinToString("\n") { it.file.path } +
                "\n)"
    }

    companion object {
        init {
            CoroutineScope(Dispatchers.Main).launch {
                getVideos()
            }
        }

        private val TAG = GeoVideoRepository::class.java.simpleName
        private lateinit var appContext: Context
        private lateinit var file: File
        private val _videos = MutableLiveData<Set<GeoVideo>?>()
        private val mutex = Mutex()

        private suspend fun getVideos(): Set<GeoVideo> =
            mutex.withLock {
                if (_videos.value == null) {
                    _videos.value =
                        readFromFile()
                }
                return _videos.value!!
            }

        private suspend fun findAllGeoVideoFiles() =
            withContext(Dispatchers.IO) {
                fun filesBelow(dir: File): Sequence<File> = sequence {
                    dir.listFiles()!!.filterNotNull().forEach { file ->
                        when {
                            file.isDirectory -> yieldAll(filesBelow(file))
                            file.name.endsWith(".geo.mp4") -> yield(file)
                        }
                    }
                }
                appContext.externalMediaDirs.filterNotNull().flatMap {
                    filesBelow(it).asIterable()
                }
            }

        private suspend fun writeToFile() =
            withContext(Dispatchers.IO) {
                file.outputStream().buffered().use {
                    ObjectOutputStream(it).writeObject(getVideos())
                }
            }.also {
                logd(TAG) { "writeToFile" }
            }

        private suspend fun readFromFile() =
            withContext(Dispatchers.IO) {
                if (!file.exists()) {
                    setOf()
                } else {
                    file.inputStream().buffered().use {
                        ObjectInputStream(it).readObject()
                    } as Set<GeoVideo>
                }
            }.also {
                logd(TAG) { "readFromFile" }
            }

    }

}
