@file:Suppress("NAME_SHADOWING")

package com.gmail.fujifruity.summerview

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jcodec.containers.mp4.boxes.MetaValue
import org.jcodec.movtool.MetadataEditor
import java.io.*
import java.util.*
import kotlin.math.absoluteValue

/**
 * Represents an existing video file which has location footprint as metadata
 * @throws IllegalArgumentException
 */
class GeoVideo private constructor(
    private val filePath: String,
    val footprints: List<Footprint>,
    alerts: List<Alert>
) : Serializable {
    // Store file and uri as string which is serializable.
    val file: File
        get() = File(filePath)
    val title: String
        get() = file.name

    /** [Alert]s in ascending order */
    private val _alerts = alerts.toMutableList()
    val alerts: List<Alert>
        get() = _alerts

    // TODO: get/set as metadata?
    private val footprintTree: TreeNode<Footprint>

    init {
        val start = System.currentTimeMillis()
        val movingFootprints = footprints.let {
            // filter out crowded footprints except for first one
            it.subList(0, 1) + it.subList(1, it.size).filter { it.point.isMoving() }
        }
        footprintTree =
            TreeNode.create(
                movingFootprints
            )
        logd(TAG) { "tree construction took ${System.currentTimeMillis() - start}ms." }
    }

    override fun toString(): String {
        val nShownFootprints = 2
        val footprintsStr = if (footprints.size > nShownFootprints * 2) {
            footprints.take(nShownFootprints) +
                    listOf("...snip ${footprints.count() - 2 * nShownFootprints}...") +
                    footprints.takeLast(nShownFootprints)
        } else {
            footprints
        }.joinToString("\n")
        return """Geovideo(
            |title=$title, file=$file,
            |alerts=$alerts
            |footprints=
            |$footprintsStr
            |)""".trimMargin()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is GeoVideo) return false
        return file == other.file
    }

    override fun hashCode(): Int {
        return Objects.hash(file)
    }

    fun getDurationMs(context: Context): Long {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, Uri.fromFile(file))
        val msecString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        return msecString.toLong()
    }

    /**
     * Create new alert and add it to the [alerts] and also write to [file] as metadata.
     * This is the only modifier which increase alerts.
     * Keeps ascending order of [alerts].
     */
    suspend fun addAlert(positionMs: Long, description: String) {
        val point = getFootprint(positionMs).point
        val alert = Alert(
            positionMs,
            point,
            description
        )
        val insertionIdx = alerts
            .binarySearchBy(positionMs) { it.positionMs }
            .let { idx -> if (idx < 0) -idx - 1 else idx }
        _alerts.add(insertionIdx, alert)
        writeMetadata(
            file,
            KEY_ALERTS,
            alerts as Serializable
        )
    }

    suspend fun removeNearestAlert(positionMs: Long) {
        // Remove one from the field
        val nearest = alerts.minBy { (it.positionMs - positionMs).absoluteValue }!!
        _alerts.remove(nearest)
        // Also remove one from the metadata
        writeMetadata(
            file,
            KEY_ALERTS,
            alerts as Serializable
        )
    }

    fun getFootprint(positionMs: Long): Footprint {
        val insertionIdx =
            footprints.binarySearchBy(positionMs) { it.positionMs }.let { insertionIdx ->
                if (insertionIdx < 0) -(insertionIdx + 1) else insertionIdx
            }
        val idx = insertionIdx.coerceAtMost(footprints.size - 1)
        return footprints[idx].interpolate(positionMs)
    }

    fun findSimilarNearestFootprint(p: Point): Footprint? {
        val similarNearest = footprintTree.findSimilarNearest(p)
        return similarNearest?.interpolate(p)
    }

    /**
     * Deletes the video from disk.
     */
    fun delete() {
        Log.i(TAG, "delete from disk: $title")
        file.delete()
    }

    companion object {
        private val TAG = GeoVideo::class.java.simpleName
        private val KEY_FOOTPRINTS =
            fourccToInt("ftpr")
        private val KEY_ALERTS =
            fourccToInt("alrt")
        private const val serialVersionUID = 1L

        private fun createMetadataEditor(file: File) = try {
            MetadataEditor.createFrom(file)
        } catch (e: NullPointerException) {
            Log.e(TAG, "cannot create metadata editor on $file", e.cause)
            null
        }

        private suspend fun writeMetadata(file: File, metadataKey: Int, s: Serializable) =
            withContext(Dispatchers.IO) {
                val bytes = serialize(s)
                val editor = createMetadataEditor(file) ?: return@withContext
                // tips: Metadata written by MetadataEditor.keyedMeta[] cause parsing error with media2.
                editor.itunesMeta[metadataKey] =
                    MetaValue.createOther(MetaValue.TYPE_STRING_UTF8, bytes)
                editor.save(true)
            }

        private suspend fun <T> readMetadata(file: File, metadataKey: Int): T? =
            withContext(Dispatchers.IO) {
                val editor = createMetadataEditor(file) ?: return@withContext null
                val metaValue = editor.itunesMeta[metadataKey]
                metaValue?.let {
                    val bytes = it.data
                    deserialize(bytes)
                } as? T
            }

        /**
         * 1. writes [Footprint]s into the mp4 file as metadata
         * 2. register the file to the media provider
         * @param points should not be empty.
         */
        suspend fun geovideonize(
            context: Context,
            mp4file: File,
            points: List<Point>,
            recordingStartErtMs: Long
        ): GeoVideo = withContext(Dispatchers.IO) {
            require(points.isNotEmpty()) { "points is empty" }
            logd(TAG) { "create footprints linked each other" }
            val footprints = run {
                val footprints = points.map {
                    val position = it.elapsedRealtime - recordingStartErtMs
                    Footprint(position, it)
                }
                if (points.size == 1) return@run footprints
                footprints.windowed(2).forEach { (f0, f1) ->
                    f0.next = f1
                    f1.prev = f0
                }
                footprints
            }
            logd(TAG) { "write footprints into the video file as metadata" }
            writeMetadata(
                mp4file,
                KEY_FOOTPRINTS,
                footprints as Serializable
            )
            writeMetadata(
                mp4file,
                KEY_ALERTS,
                listOf<Alert>() as Serializable
            )
            Log.i(TAG, "register the video file to the media provider")
            context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.DATA, mp4file.path)
                }
            )!!
            GeoVideo(
                mp4file.path,
                footprints,
                listOf()
            )
        }

        /**
         * @throws IllegalArgumentException
         */
        private suspend fun fromFile(file: File): GeoVideo =
            withContext(Dispatchers.IO) {
                val name = file.name
                require(file.exists()) { "file not found: $name" }
                val footprints = readMetadata(file, KEY_FOOTPRINTS) as? List<Footprint>
                    ?: throw IllegalArgumentException("no footprints: $name")
                require(footprints.isNotEmpty()) { "empty footprints: $name" }
                val alerts = readMetadata(file, KEY_ALERTS) as? List<Alert>
                    ?: throw IllegalArgumentException("no alerts: $name")
                GeoVideo(
                    file.path,
                    footprints,
                    alerts
                )
            }

        suspend fun fromFileOrNull(file: File) =
            try {
                fromFile(file)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "${e.message}")
                null
            }

        /**
        val actual = GeoVideo.fourccToInt("locs")
        assertEquals(1819239283, actual)
         */
        private fun fourccToInt(fourcc: String): Int =
            fourcc.reversed().mapIndexed { i, c -> c.toInt() shl 8 * i }.sum()

        /**
        val actual = GeoVideo.intToFourcc(1819239283)
        assertEquals("locs", actual)
         */
        private fun intToFourcc(n: Int): String =
            n.toString(2).padStart(8 * 4, '0')
                .chunked(8) { it.toString().toInt(2).toChar() }.joinToString("")

        private fun serialize(s: Serializable) =
            ByteArrayOutputStream().also {
                ObjectOutputStream(it).use {
                    it.writeObject(s)
                }
            }.toByteArray()

        private fun deserialize(b: ByteArray) =
            try {
                ObjectInputStream(b.inputStream()).use {
                    it.readObject()
                }
            } catch (e: Exception) {
                Log.e(TAG, "${e.javaClass.simpleName}: ${e.message}")
                null
            }
    }

    /**
     * @param positionMs Position in this video in ms
     */
    data class Alert(val positionMs: Long, val point: Point, val description: String) :
        Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

}

