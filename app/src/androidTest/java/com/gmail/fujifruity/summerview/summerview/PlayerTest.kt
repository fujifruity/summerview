package com.gmail.fujifruity.summerview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.Surface
import androidx.core.view.drawToBitmap
import androidx.core.view.isVisible
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gmail.fujifruity.summerview.*
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.absoluteValue
import kotlin.random.Random


/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class PlayerTest {

    fun player(context: Context, surface: Surface): PlayerWrapper
//        = MediaPlayerWrapper(context,surface)
            =
        VideoPlayerWrapper(context, surface)

    val testPause = "test_pause.geo.mp4"
    val testSlow = "test_slow.geo.mp4"
    val testFast = "test_fast.geo.mp4"
    val upperBoundMeter = 1.5

    @Test
    fun pauseInVideo() = testWithTwoVideos(
        testPause,
        testFast,
        upperBoundMeter,
        object {}.javaClass.enclosingMethod!!.name
    )

    @Test
    fun pauseInDrive() = testWithTwoVideos(
        testSlow,
        testPause,
        upperBoundMeter,
        object {}.javaClass.enclosingMethod!!.name
    )

    @Test
    fun slowDrive() = testWithTwoVideos(
        testFast,
        testSlow,
        upperBoundMeter,
        object {}.javaClass.enclosingMethod!!.name
    )

    @Test
    fun fastDrive() = testWithTwoVideos(
        testSlow,
        testFast,
        upperBoundMeter,
        object {}.javaClass.enclosingMethod!!.name
    )

    fun testWithTwoVideos(
        testVideoName: String,
        trailVideoName: String,
        meanAbsoluteDelayUpperBound: Double,
        testName: String
    ) {
        val playerDelays = mutableListOf<Double>()
        val delayErts = mutableListOf<Double>()
        var testDurationMs = 0L
        val scenario = launchActivity<PlayerTestActivity>()
        val mainScope = CoroutineScope(Dispatchers.Main)
        scenario.onActivity { activity ->
            val repository = GeoVideoRepository(activity.application)
            val videos = repository.getVideosBlocking()
            val testVideos = setOf(videos.find { it.title == testVideoName }!!)
            val trailVideo = videos.find { it.title == trailVideoName }!!
            testDurationMs = trailVideo.getDurationMs(activity) + 1000
            val syncStartNs = System.nanoTime()
            var playerSync: PlayerSync? = null
            val storeErrorMeter = { nearestFootprint: Footprint ->
                val playingFootprint =
                    playerSync!!.currentVideo!!.getFootprint(activity.player!!.currentPositionMs)
                val errorMeter = nearestFootprint.distMeter(playingFootprint)
                val isGettingBehind = playingFootprint.positionMs < nearestFootprint.positionMs
                val errorSign = if (isGettingBehind) 1 else -1
                val playerDelayMeter = errorSign * errorMeter
                val elapsedSec = (System.nanoTime() - syncStartNs) / 1e9
                playerDelays.add(playerDelayMeter)
                delayErts.add(elapsedSec)
                logd(TAG) { "elapsedS, delayM=%.1f, %.1f".format(elapsedSec, playerDelayMeter) }
                Unit
            }
            val binding = activity.binding
            activity.player = player(activity, binding.surfaceView.holder.surface)
            playerSync = PlayerSync(
                activity.player!!,
                testVideos,
                storeErrorMeter,
                { binding.outOfCourseTextView.isVisible = true },
                { binding.outOfCourseTextView.isVisible = false }
            )
            logd(TAG) { "Start sync thread" }
            mainScope.launch {
                val footprints = trailVideo.footprints.asSequence()
                flow<Point> {
                    emit(footprints.first().point)
                    footprints.windowed(2).asFlow().map { (r0, r1) ->
                        val interval = (r1.positionMs - r0.positionMs)
                        delay(interval)
                        binding.gnssAccuracyView.update(r1.point.accuracy)
                        r1.point
                    }.collect {
                        emit(it)
                    }
                }.collect {
                    playerSync.synchronizePlayback(it)
                }
            }
            logd(TAG) { "start alert frame loop" }
            mainScope.launch {
                binding.alertFrame.alertFrameLoop(
                    activity.player!!,
                    { playerSync.currentVideo?.alerts }
                )
            }
            activity.handler.postDelayed({
                logd(TAG) { "create a plotting of ${delayErts.size} delays and save it" }
                val series = delayErts.zip(playerDelays)
                    .map { (ert, delay) -> DataPoint(ert, delay) }
                    .let { LineGraphSeries(it.toTypedArray()) }
                val playerClass = activity.player!!::class.java.simpleName.dropLast(7)
                val testTitle = "$testName $playerClass"
                val bitmap = binding.graphView.apply {
                    addSeries(series)
                    setBackgroundColor(Color.WHITE)
                    title = testTitle
                    viewport.apply {
                        calcCompleteRange()
                        isXAxisBoundsManual = true
                        setMaxX(getMaxX(true))
                    }
                }.drawToBitmap()
                ByteArrayOutputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, it)
                    val file =
                        File(
                            activity.externalMediaDirs.first(),
                            "${testTitle.replace(" ", "_")}.png"
                        )
                    file.writeBytes(it.toByteArray())
                }
            }, testDurationMs)
        }

        Thread.sleep(testDurationMs + 2000)
        val meanAbsoluteDelay = playerDelays.map { it.absoluteValue }.sum() / playerDelays.size
        logd(TAG) { "meanAbsoluteDelay=$meanAbsoluteDelay" }
        assertTrue(
            "expect=$meanAbsoluteDelayUpperBound < actual=$meanAbsoluteDelay",
            meanAbsoluteDelayUpperBound >= meanAbsoluteDelay
        )

    }

    @Test
    fun gettingOutAndComingBackToCourse() {
        val scenario = launchActivity<PlayerTestActivity>()
        scenario.onActivity { activity ->
            val videos = GeoVideoRepository(
                activity.application
            ).getVideosBlocking()
            val testVideo = videos.first { it.title == testFast }
            val binding = activity.binding
            activity.player = player(activity, binding.surfaceView.holder.surface)
            val playerSync = PlayerSync(
                activity.player!!,
                setOf(testVideo),
                {},
                { binding.outOfCourseTextView.isVisible = true },
                { binding.outOfCourseTextView.isVisible = false }
            )
            logd(TAG) { "Start sync thread" }
            CoroutineScope(Dispatchers.Main).launch {
                // let pn: n-th footprint's point of test video (being played),
                //     px: invalid point
                // sequence of trail points is like:
                // pv0, pv1, pv2, px, px, px, pv0, pv1, pv2
                //                ^gettingOut    ^comingBack
                val trailPoints = run {
                    val threeValidPoints = testVideo.footprints.take(3).map { it.point }
                    val invalidPoint = Point(
                        0.0,
                        0.0,
                        0,
                        0.0,
                        0.0,
                        10.0
                    )
                    val threeInvalidPoints = List(3) { invalidPoint }
                    threeValidPoints + threeInvalidPoints + threeValidPoints
                }
                trailPoints.asSequence().forEachIndexed { index, point ->
                    delay(1000)
                    logd(TAG) { "index=${index}" }
                    playerSync.synchronizePlayback(point)
                    val isCorrectVisibility = binding.outOfCourseTextView.isVisible == index in 3..5
                    assertTrue(isCorrectVisibility)
                }
            }
        }
        Thread.sleep(12000)
    }

    @Test
    fun sampleGraph() {
        val scenario = launchActivity<PlayerTestActivity>()
        scenario.onActivity { activity ->
            val context = activity.applicationContext
            activity.player =
                VideoPlayerWrapper(
                    context,
                    activity.binding.surfaceView.holder.surface
                )
            val series = (0..30).zip(List(30) { Random.nextDouble() })
                .map { (ert, delay) -> DataPoint(ert.toDouble(), delay) }
                .let { LineGraphSeries(it.toTypedArray()) }
            val bitmap = GraphView(context).apply {
                addSeries(series)
                setBackgroundColor(Color.WHITE)
                viewport.apply {
                    calcCompleteRange()
                    isXAxisBoundsManual = true
                    setMaxX(getMaxX(true))
                }
                this.layout(0, 0, 1000, 300)
                // TODO: cannot draw grids
            }.drawToBitmap()
            ByteArrayOutputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, it)
                val file = File(activity.externalMediaDirs.first(), "sampleGraph.png")
                file.writeBytes(it.toByteArray())
            }
        }
        Thread.sleep(2000)
    }

    companion object {
        private val TAG = PlayerTest::class.java.simpleName
    }
}