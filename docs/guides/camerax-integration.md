# CameraX Integration Guide

## Overview

This guide provides comprehensive instructions for integrating CameraX with the Pose Coach Android SDK for optimal pose detection performance. CameraX provides a consistent camera API across Android devices and versions.

## Prerequisites

- Android API level 21 (Android 5.0) or higher
- CameraX version 1.3.0 or later
- Camera permissions properly configured

## Dependencies

Add these dependencies to your app-level `build.gradle.kts`:

```kotlin
dependencies {
    // CameraX core library
    implementation("androidx.camera:camera-core:1.3.0")

    // CameraX Camera2 extensions
    implementation("androidx.camera:camera-camera2:1.3.0")

    // CameraX Lifecycle library
    implementation("androidx.camera:camera-lifecycle:1.3.0")

    // CameraX View class
    implementation("androidx.camera:camera-view:1.3.0")

    // CameraX Extensions library (optional)
    implementation("androidx.camera:camera-extensions:1.3.0")

    // Pose Coach integration
    implementation("com.posecoach:camera-integration:1.0.0")
}
```

## Basic CameraX Setup

### 1. Camera Manager Implementation

Create a comprehensive camera manager for pose detection:

```kotlin
class PoseCoachCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

    // Pose detection components
    private val poseRepository = MediaPipePoseRepository(context)
    private var poseAnalyzer: PoseAnalyzer? = null

    // Configuration
    private var targetResolution = Size(640, 480)
    private var targetRotation = Surface.ROTATION_0
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    interface PoseCallback {
        fun onPoseDetected(result: PoseLandmarkResult?)
        fun onError(exception: Exception)
        fun onFpsUpdate(fps: Float)
    }

    private var poseCallback: PoseCallback? = null

    fun setPoseCallback(callback: PoseCallback) {
        this.poseCallback = callback
    }

    suspend fun startCamera(previewView: PreviewView) {
        try {
            // Get camera provider
            cameraProvider = ProcessCameraProvider.getInstance(context).await()

            // Bind use cases
            bindCameraUseCases(previewView)

        } catch (exception: Exception) {
            poseCallback?.onError(exception)
        }
    }

    private fun bindCameraUseCases(previewView: PreviewView) {
        val cameraProvider = cameraProvider ?: return

        // Build preview use case
        preview = buildPreview().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        // Build image analysis use case
        imageAnalyzer = buildImageAnalysis()

        try {
            // Unbind all use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to lifecycle
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            // Set up camera controls
            setupCameraControls()

        } catch (exception: Exception) {
            poseCallback?.onError(exception)
        }
    }

    private fun buildPreview(): Preview {
        return Preview.Builder()
            .setTargetResolution(targetResolution)
            .setTargetRotation(targetRotation)
            .build()
    }

    private fun buildImageAnalysis(): ImageAnalysis {
        poseAnalyzer = PoseAnalyzer(
            poseRepository = poseRepository,
            callback = poseCallback
        )

        return ImageAnalysis.Builder()
            .setTargetResolution(targetResolution)
            .setTargetRotation(targetRotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analyzer ->
                analyzer.setAnalyzer(
                    ContextCompat.getMainExecutor(context),
                    poseAnalyzer!!
                )
            }
    }

    private fun setupCameraControls() {
        val camera = camera ?: return

        // Enable auto-focus if available
        camera.cameraControl.enableTorch(false)

        // Set up focus metering if needed
        // This can be used for tap-to-focus functionality
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        poseAnalyzer?.cleanup()
    }

    // Configuration methods
    fun setTargetResolution(resolution: Size) {
        targetResolution = resolution
    }

    fun setCameraSelector(selector: CameraSelector) {
        cameraSelector = selector
    }

    fun setTargetRotation(rotation: Int) {
        targetRotation = rotation
    }
}
```

### 2. Advanced Pose Analyzer

Implement a high-performance pose analyzer:

```kotlin
class PoseAnalyzer(
    private val poseRepository: MediaPipePoseRepository,
    private val callback: PoseCoachCameraManager.PoseCallback?
) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTimestamp = 0L
    private val fpsCalculator = FpsCalculator()
    private val frameSkipInterval = 33L // ~30 FPS

    // Performance monitoring
    private var processingTimes = mutableListOf<Long>()
    private val maxProcessingTimes = 100

    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()

        // Skip frames to maintain target FPS
        if (currentTimestamp - lastAnalyzedTimestamp < frameSkipInterval) {
            imageProxy.close()
            return
        }

        lastAnalyzedTimestamp = currentTimestamp
        val startTime = System.nanoTime()

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            try {
                // Convert ImageProxy to format expected by MediaPipe
                val bitmap = imageProxyToBitmap(imageProxy)

                // Perform pose detection
                lifecycleScope.launch(Dispatchers.Default) {
                    try {
                        val result = poseRepository.detectPose(
                            image = mediaImage,
                            rotationDegrees = imageProxy.imageInfo.rotationDegrees
                        )

                        // Calculate processing time
                        val processingTime = (System.nanoTime() - startTime) / 1_000_000L
                        updatePerformanceMetrics(processingTime)

                        // Update FPS
                        val fps = fpsCalculator.calculateFps()

                        // Deliver results on main thread
                        withContext(Dispatchers.Main) {
                            callback?.onPoseDetected(result)
                            callback?.onFpsUpdate(fps)
                        }

                    } catch (exception: Exception) {
                        withContext(Dispatchers.Main) {
                            callback?.onError(exception)
                        }
                    } finally {
                        imageProxy.close()
                    }
                }

            } catch (exception: Exception) {
                callback?.onError(exception)
                imageProxy.close()
            }
        } else {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        // Convert YUV_420_888 to RGB bitmap
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val imageBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun updatePerformanceMetrics(processingTime: Long) {
        processingTimes.add(processingTime)
        if (processingTimes.size > maxProcessingTimes) {
            processingTimes.removeAt(0)
        }
    }

    fun getAverageProcessingTime(): Float {
        return if (processingTimes.isNotEmpty()) {
            processingTimes.average().toFloat()
        } else {
            0f
        }
    }

    fun cleanup() {
        // Clean up resources
        processingTimes.clear()
    }
}
```

### 3. FPS Calculator

Monitor performance with FPS tracking:

```kotlin
class FpsCalculator {
    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var currentFps = 0f

    fun calculateFps(): Float {
        frameCount++
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastFpsTimestamp

        if (elapsed >= 1000) { // Update every second
            currentFps = (frameCount * 1000f) / elapsed
            frameCount = 0
            lastFpsTimestamp = currentTime
        }

        return currentFps
    }
}
```

## Camera Configuration Options

### 1. Resolution Configuration

Optimize resolution for performance vs. quality:

```kotlin
class CameraConfig {
    companion object {
        // Performance-optimized resolutions
        val RESOLUTION_LOW = Size(320, 240)      // Very fast, lower accuracy
        val RESOLUTION_MEDIUM = Size(640, 480)    // Balanced (recommended)
        val RESOLUTION_HIGH = Size(1280, 720)     // Higher accuracy, slower

        // Device-specific optimization
        fun getOptimalResolution(context: Context): Size {
            val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay
            val point = Point()
            display.getSize(point)

            return when {
                point.x * point.y < 500_000 -> RESOLUTION_LOW    // Low-end devices
                point.x * point.y < 1_000_000 -> RESOLUTION_MEDIUM // Mid-range devices
                else -> RESOLUTION_HIGH                            // High-end devices
            }
        }
    }
}
```

### 2. Camera Selector Configuration

Choose the best camera for pose detection:

```kotlin
fun getCameraSelector(preferFront: Boolean = false): CameraSelector {
    val builder = CameraSelector.Builder()

    return if (preferFront) {
        builder.requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
    } else {
        // Back camera typically has better image quality
        builder.requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
    }
}

// Get available cameras
fun getAvailableCameras(cameraProvider: ProcessCameraProvider): List<CameraInfo> {
    val availableCameras = mutableListOf<CameraInfo>()

    if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
        availableCameras.add(
            cameraProvider.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA)
        )
    }

    if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
        availableCameras.add(
            cameraProvider.getCameraInfo(CameraSelector.DEFAULT_FRONT_CAMERA)
        )
    }

    return availableCameras
}
```

## Performance Optimization

### 1. Threading Configuration

Optimize threading for better performance:

```kotlin
class OptimizedPoseAnalyzer(
    private val poseRepository: MediaPipePoseRepository,
    private val callback: PoseCoachCameraManager.PoseCallback?
) : ImageAnalysis.Analyzer {

    // Use custom executor for CPU-intensive operations
    private val analysisExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PoseAnalysisThread").apply {
            priority = Thread.NORM_PRIORITY + 1 // Slightly higher priority
        }
    }

    // Use separate executor for MediaPipe processing
    private val mediaPipeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "MediaPipeThread").apply {
            priority = Thread.MAX_PRIORITY // Highest priority for pose detection
        }
    }

    override fun analyze(imageProxy: ImageProxy) {
        analysisExecutor.execute {
            processImage(imageProxy)
        }
    }

    private fun processImage(imageProxy: ImageProxy) {
        mediaPipeExecutor.execute {
            // MediaPipe processing
            try {
                val result = poseRepository.detectPose(
                    image = imageProxy.image!!,
                    rotationDegrees = imageProxy.imageInfo.rotationDegrees
                )

                // Deliver result on main thread
                Handler(Looper.getMainLooper()).post {
                    callback?.onPoseDetected(result)
                }

            } catch (exception: Exception) {
                Handler(Looper.getMainLooper()).post {
                    callback?.onError(exception)
                }
            } finally {
                imageProxy.close()
            }
        }
    }

    fun cleanup() {
        analysisExecutor.shutdown()
        mediaPipeExecutor.shutdown()
    }
}
```

### 2. Memory Management

Implement proper memory management:

```kotlin
class MemoryOptimizedCameraManager {
    private val imagePool = Pools.SimplePool<Bitmap>(5)
    private val maxMemoryCache = (Runtime.getRuntime().maxMemory() / 8).toInt()

    fun recycleImage(bitmap: Bitmap?) {
        bitmap?.let {
            if (!it.isRecycled && imagePool.acquire() == null) {
                imagePool.release(it)
            }
        }
    }

    fun getRecycledBitmap(width: Int, height: Int): Bitmap? {
        val bitmap = imagePool.acquire()
        return if (bitmap != null && bitmap.width == width && bitmap.height == height) {
            bitmap
        } else {
            bitmap?.recycle()
            null
        }
    }

    fun cleanupMemory() {
        var bitmap = imagePool.acquire()
        while (bitmap != null) {
            bitmap.recycle()
            bitmap = imagePool.acquire()
        }

        // Force garbage collection if memory is low
        if (Runtime.getRuntime().freeMemory() < maxMemoryCache) {
            System.gc()
        }
    }
}
```

## Error Handling

### 1. Camera Initialization Errors

Handle common camera initialization issues:

```kotlin
class CameraErrorHandler {
    companion object {
        fun handleCameraError(exception: Exception): String {
            return when (exception) {
                is CameraUnavailableException ->
                    "Camera is unavailable. Please check if another app is using the camera."

                is IllegalArgumentException ->
                    "Invalid camera configuration. Please check camera settings."

                is SecurityException ->
                    "Camera permission denied. Please grant camera permission."

                is RuntimeException -> {
                    when {
                        exception.message?.contains("Camera is being used") == true ->
                            "Camera is already in use by another application."
                        exception.message?.contains("Too many open cameras") == true ->
                            "Too many camera instances. Please close other camera apps."
                        else -> "Camera runtime error: ${exception.message}"
                    }
                }

                else -> "Unknown camera error: ${exception.message}"
            }
        }
    }
}
```

### 2. Pose Detection Error Recovery

Implement robust error recovery:

```kotlin
class RobustPoseAnalyzer(
    private val poseRepository: MediaPipePoseRepository,
    private val callback: PoseCoachCameraManager.PoseCallback?
) : ImageAnalysis.Analyzer {

    private var consecutiveErrors = 0
    private val maxConsecutiveErrors = 5
    private var isRecovering = false

    override fun analyze(imageProxy: ImageProxy) {
        if (isRecovering) {
            imageProxy.close()
            return
        }

        try {
            // Process image
            processImageSafely(imageProxy)

            // Reset error counter on success
            consecutiveErrors = 0

        } catch (exception: Exception) {
            handleAnalysisError(exception)
            imageProxy.close()
        }
    }

    private fun processImageSafely(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: throw IllegalStateException("Image is null")

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val result = poseRepository.detectPose(
                    image = mediaImage,
                    rotationDegrees = imageProxy.imageInfo.rotationDegrees
                )

                withContext(Dispatchers.Main) {
                    callback?.onPoseDetected(result)
                }

            } catch (exception: Exception) {
                handlePoseDetectionError(exception)
            } finally {
                imageProxy.close()
            }
        }
    }

    private fun handleAnalysisError(exception: Exception) {
        consecutiveErrors++

        if (consecutiveErrors >= maxConsecutiveErrors) {
            // Enter recovery mode
            isRecovering = true

            // Attempt recovery after delay
            Handler(Looper.getMainLooper()).postDelayed({
                attemptRecovery()
            }, 2000)
        }

        callback?.onError(exception)
    }

    private fun attemptRecovery() {
        try {
            // Reinitialize MediaPipe if needed
            poseRepository.reinitialize()

            // Exit recovery mode
            isRecovering = false
            consecutiveErrors = 0

        } catch (exception: Exception) {
            // Recovery failed, try again later
            Handler(Looper.getMainLooper()).postDelayed({
                attemptRecovery()
            }, 5000)
        }
    }

    private fun handlePoseDetectionError(exception: Exception) {
        when (exception) {
            is OutOfMemoryError -> {
                // Handle memory issues
                System.gc()
                callback?.onError(Exception("Low memory, reducing processing quality"))
            }
            is IllegalStateException -> {
                // Handle MediaPipe state issues
                callback?.onError(Exception("Pose detection temporarily unavailable"))
            }
            else -> {
                callback?.onError(exception)
            }
        }
    }
}
```

## Usage Example

Here's a complete example of using the CameraX integration:

```kotlin
class PoseDetectionActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var cameraManager: PoseCoachCameraManager
    private lateinit var overlayView: PoseOverlayView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pose_detection)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)

        // Initialize camera manager
        cameraManager = PoseCoachCameraManager(this, this)
        cameraManager.setPoseCallback(object : PoseCoachCameraManager.PoseCallback {
            override fun onPoseDetected(result: PoseLandmarkResult?) {
                result?.let {
                    overlayView.updatePose(it.landmarks)
                }
            }

            override fun onError(exception: Exception) {
                showError(CameraErrorHandler.handleCameraError(exception))
            }

            override fun onFpsUpdate(fps: Float) {
                updateFpsDisplay(fps)
            }
        })

        // Configure camera settings
        cameraManager.setTargetResolution(CameraConfig.getOptimalResolution(this))
        cameraManager.setCameraSelector(getCameraSelector(preferFront = false))

        // Request camera permission
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun startCamera() {
        lifecycleScope.launch {
            try {
                cameraManager.startCamera(previewView)
            } catch (exception: Exception) {
                showError("Failed to start camera: ${exception.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.stopCamera()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun updateFpsDisplay(fps: Float) {
        // Update FPS indicator in UI
    }
}
```

## Best Practices

1. **Use appropriate resolution**: Balance performance vs. accuracy based on device capabilities
2. **Implement proper threading**: Keep UI thread free for smooth user experience
3. **Handle errors gracefully**: Provide fallback mechanisms for camera failures
4. **Monitor performance**: Track FPS and processing times for optimization
5. **Manage memory**: Properly recycle bitmaps and clean up resources
6. **Test on various devices**: Ensure compatibility across different Android devices

## Troubleshooting

### Common Issues

- **Camera not available**: Check permissions and device capabilities
- **Poor performance**: Reduce resolution or adjust frame skip interval
- **Memory issues**: Implement proper bitmap recycling
- **Pose detection failures**: Ensure good lighting and proper camera setup

### Performance Tips

- Use `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` for real-time processing
- Skip frames when processing can't keep up with camera framerate
- Use background threads for CPU-intensive operations
- Consider using CameraX Extensions for enhanced capabilities

---

This guide provides a solid foundation for integrating CameraX with pose detection. Adjust configurations based on your specific requirements and device constraints.