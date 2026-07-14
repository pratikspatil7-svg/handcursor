package com.example.handcursor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.hypot

class OverlayCursorService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private lateinit var cursorView: CursorView
    private lateinit var cursorParams: WindowManager.LayoutParams

    private lateinit var cameraExecutor: ExecutorService
    private var handLandmarker: HandLandmarker? = null

    // Exponential moving average smoothing for cursor position
    private var smoothX = -1f
    private var smoothY = -1f
    private val smoothingFactor = 0.35f // higher = snappier, lower = smoother

    private var isPinching = false
    private val pinchThreshold = 0.07f // normalized distance; tune per-hand/camera

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        setupOverlay()
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupHandLandmarker()
        startCamera()
    }

    override fun onBind(intent: android.content.Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startForegroundNotification() {
        val channelId = "hand_cursor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Hand Cursor", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("HandCursor active")
            .setContentText("Tracking your hand to control the cursor")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(1, notification)
        }
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        cursorView = CursorView(this)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        cursorParams = WindowManager.LayoutParams(
            120, 120,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        windowManager.addView(cursorView, cursorParams)
    }

    private fun setupHandLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task") // place this file in app/src/main/assets
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener(::onHandResult)
            .setErrorListener { e -> Log.e("HandCursor", "HandLandmarker error", e) }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processFrame(imageProxy)
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis
            )
        }, androidx.core.content.ContextCompat.getMainExecutor(this))
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        if (bitmap != null) {
            val mpImage = BitmapImageBuilder(bitmap).build()
            handLandmarker?.detectAsync(mpImage, System.currentTimeMillis())
        }
        imageProxy.close()
    }

    private fun onHandResult(result: HandLandmarkerResult, input: com.google.mediapipe.framework.image.MPImage) {
        if (result.landmarks().isEmpty()) return
        val hand = result.landmarks()[0]

        val indexTip = hand[8]   // fingertip used to position the cursor
        val thumbTip = hand[4]

        // Mirror X so moving your hand right (as you see it) moves the cursor right on screen
        val normX = 1f - indexTip.x()
        val normY = indexTip.y()

        val displayMetrics = resources.displayMetrics
        val targetX = normX * displayMetrics.widthPixels
        val targetY = normY * displayMetrics.heightPixels

        if (smoothX < 0) {
            smoothX = targetX; smoothY = targetY
        } else {
            smoothX += (targetX - smoothX) * smoothingFactor
            smoothY += (targetY - smoothY) * smoothingFactor
        }

        val pinchDist = hypot((indexTip.x() - thumbTip.x()).toDouble(), (indexTip.y() - thumbTip.y()).toDouble())
        val pinchingNow = pinchDist < pinchThreshold

        moveCursorAndMaybeTap(pinchingNow)
    }

    private fun moveCursorAndMaybeTap(pinchingNow: Boolean) {
        // Must touch WindowManager / UI on main thread
        cursorView.post {
            cursorParams.x = (smoothX - cursorView.width / 2f).toInt()
            cursorParams.y = (smoothY - cursorView.height / 2f).toInt()
            windowManager.updateViewLayout(cursorView, cursorParams)
            cursorView.pinching = pinchingNow
            cursorView.invalidate()

            // Rising edge only: fire one tap per pinch, not continuously while held
            if (pinchingNow && !isPinching) {
                GestureAccessibilityService.instance?.performTap(smoothX, smoothY)
            }
            isPinching = pinchingNow
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handLandmarker?.close()
        cameraExecutor.shutdown()
        if (::cursorView.isInitialized) {
            windowManager.removeView(cursorView)
        }
    }
}

/** Converts a CameraX YUV_420_888 ImageProxy to an ARGB Bitmap. */
fun ImageProxy.toBitmap(): Bitmap? {
    return try {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
        val bytes = out.toByteArray()
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        Log.e("HandCursor", "toBitmap failed", e)
        null
    }
}
