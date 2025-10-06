package com.posecoach.corepose.repository

import android.content.Context
import android.graphics.Bitmap
import com.posecoach.corepose.models.PoseLandmarkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class FakePoseRepository : PoseRepository {
    private var listener: PoseDetectionListener? = null
    private var isRunning = false
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    suspend fun init(@Suppress("UNUSED_PARAMETER") context: Context, @Suppress("UNUSED_PARAMETER") modelPath: String?) {
        Timber.d("FakePoseRepository initialized")
        delay(100)
    }

    fun start(listener: PoseDetectionListener) {
        this.listener = listener
        isRunning = true
        Timber.d("FakePoseRepository started")
    }

    fun stop() {
        listener = null
        isRunning = false
        Timber.d("FakePoseRepository stopped")
    }

    override suspend fun detectAsync(bitmap: Bitmap, timestampMs: Long) {
        detectAsync(bitmap, timestampMs, 0) // Default to 0° rotation
    }

    override suspend fun detectAsync(bitmap: Bitmap, timestampMs: Long, rotationDegrees: Int) {
        if (!isRunning || listener == null) {
            Timber.w("Repository not running or no listener set")
            return
        }

        coroutineScope.launch {
            delay(20)

            val landmarks = List(33) { index ->
                PoseLandmarkResult.Landmark(
                    x = 0.5f + (index * 0.01f),
                    y = 0.5f + (index * 0.01f),
                    z = 0.0f,
                    visibility = 0.9f,
                    presence = 0.95f
                )
            }

            val result = PoseLandmarkResult(
                landmarks = landmarks,
                worldLandmarks = landmarks,
                timestampMs = timestampMs,
                inferenceTimeMs = 20
            )

            Timber.d("Fake pose detected with rotation $rotationDegrees°, inference time: ${result.inferenceTimeMs}ms")
            listener?.onPoseDetected(result)
        }
    }

    fun isRunning(): Boolean = isRunning

    override fun release() {
        stop()
        Timber.d("FakePoseRepository released")
    }

    fun generateStablePose(timestampMs: Long): PoseLandmarkResult {
        val landmarks = generateRealisticPoseLandmarks()
        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = timestampMs,
            inferenceTimeMs = 15
        )
    }

    fun generateMultiPersonPose(timestampMs: Long, personCount: Int = 2): List<PoseLandmarkResult> {
        return (0 until personCount).map { personIndex ->
            val landmarks = generateRealisticPoseLandmarks(personOffset = personIndex * 0.3f)
            PoseLandmarkResult(
                landmarks = landmarks,
                worldLandmarks = landmarks,
                timestampMs = timestampMs,
                inferenceTimeMs = (18 + personIndex * 2).toLong()
            )
        }
    }

    fun generateNoPoseDetected(@Suppress("UNUSED_PARAMETER") timestampMs: Long): PoseLandmarkResult? = null

    fun generateLowVisibilityPose(timestampMs: Long): PoseLandmarkResult {
        val landmarks = generateRealisticPoseLandmarks(lowVisibility = true)
        return PoseLandmarkResult(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = timestampMs,
            inferenceTimeMs = 25 // Higher inference time for poor conditions
        )
    }

    private fun generateRealisticPoseLandmarks(
        personOffset: Float = 0.0f,
        lowVisibility: Boolean = false
    ): List<PoseLandmarkResult.Landmark> {
        val baseVisibility = if (lowVisibility) 0.4f else 0.95f
        val basePresence = if (lowVisibility) 0.6f else 0.98f

        return List(33) { index ->
            val (x, y, z) = getRealisticLandmarkPosition(index, personOffset)
            val visibility = baseVisibility + (Math.random().toFloat() * 0.1f - 0.05f)
            val presence = basePresence + (Math.random().toFloat() * 0.05f - 0.025f)

            PoseLandmarkResult.Landmark(
                x = x.coerceIn(0.0f, 1.0f),
                y = y.coerceIn(0.0f, 1.0f),
                z = z,
                visibility = visibility.coerceIn(0.0f, 1.0f),
                presence = presence.coerceIn(0.0f, 1.0f)
            )
        }
    }

    private fun getRealisticLandmarkPosition(landmarkIndex: Int, offset: Float): Triple<Float, Float, Float> {
        val baseX = 0.5f + offset
        val baseY = 0.5f

        return when (landmarkIndex) {
            // Face landmarks
            0 -> Triple(baseX, 0.15f, -0.1f) // NOSE
            1, 4 -> Triple(baseX - 0.02f, 0.14f, -0.08f) // Eye inner
            2, 5 -> Triple(baseX - 0.04f, 0.14f, -0.08f) // Eye
            3, 6 -> Triple(baseX - 0.06f, 0.14f, -0.08f) // Eye outer
            7, 8 -> Triple(baseX - 0.08f, 0.16f, -0.05f) // Ears
            9, 10 -> Triple(baseX - 0.03f, 0.18f, -0.06f) // Mouth

            // Upper body
            11 -> Triple(baseX - 0.15f, 0.35f, -0.05f) // LEFT_SHOULDER
            12 -> Triple(baseX + 0.15f, 0.35f, -0.05f) // RIGHT_SHOULDER
            13 -> Triple(baseX - 0.25f, 0.5f, -0.1f) // LEFT_ELBOW
            14 -> Triple(baseX + 0.25f, 0.5f, -0.1f) // RIGHT_ELBOW
            15 -> Triple(baseX - 0.3f, 0.65f, -0.15f) // LEFT_WRIST
            16 -> Triple(baseX + 0.3f, 0.65f, -0.15f) // RIGHT_WRIST

            // Hand landmarks
            17, 19, 21 -> Triple(baseX - 0.32f, 0.67f, -0.18f) // Left hand
            18, 20, 22 -> Triple(baseX + 0.32f, 0.67f, -0.18f) // Right hand

            // Lower body
            23 -> Triple(baseX - 0.1f, 0.55f, 0.0f) // LEFT_HIP
            24 -> Triple(baseX + 0.1f, 0.55f, 0.0f) // RIGHT_HIP
            25 -> Triple(baseX - 0.12f, 0.75f, 0.05f) // LEFT_KNEE
            26 -> Triple(baseX + 0.12f, 0.75f, 0.05f) // RIGHT_KNEE
            27 -> Triple(baseX - 0.1f, 0.9f, 0.1f) // LEFT_ANKLE
            28 -> Triple(baseX + 0.1f, 0.9f, 0.1f) // RIGHT_ANKLE

            // Foot landmarks
            29, 31 -> Triple(baseX - 0.12f, 0.95f, 0.12f) // Left foot
            30, 32 -> Triple(baseX + 0.12f, 0.95f, 0.12f) // Right foot

            else -> Triple(baseX, baseY, 0.0f)
        }
    }
}