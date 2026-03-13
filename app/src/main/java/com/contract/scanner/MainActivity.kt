package com.contract.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.contract.scanner.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: AppPreferences

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var camera: Camera? = null

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val executor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }

    // Regex pattern for contract number detection (e.g., № 123/456, №123, etc.)
    private val contractPattern = Regex("""\d+/?\d*""")

    // Stabilization: track last detected numbers to avoid flickering
    private val recentDetections = mutableSetOf<String>()
    private var stableContractNumber: String? = null
    private var detectionCount = 0
    private val stabilizationThreshold = 1

    companion object {
        private const val TAG = "ContractScanner"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AppPreferences(this)

        setupUI()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun setupUI() {
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.openButton.setOnClickListener {
            stableContractNumber?.let { number ->
                openBrowser(number)
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.permission_required),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        // Preview use case
        preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.previewView.surfaceProvider)
        }

        // Image Analysis use case for OCR
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(Size(1280, 720))
            .build()
            .also { analysis ->
                analysis.setAnalyzer(executor) { imageProxy ->
                    processImageProxy(imageProxy)
                }
            }

        // Select back camera
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this as LifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                handleTextRecognition(visionText.text)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Text recognition failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun handleTextRecognition(text: String) {
        val lines = text.split("\n")
        var foundContract: String? = null
        var foundBoundingBoxes: List<android.graphics.Rect> = emptyList()

        for (line in lines) {
            val match = contractPattern.find(line)
            if (match != null) {
                foundContract = match.value.trim()
                break
            }
        }

        runOnUiThread {
            updateUI(foundContract, emptyList())
        }
    }

    private fun updateUI(contractNumber: String?, boundingBoxes: List<android.graphics.Rect>) {
        if (contractNumber != null) {
            // Add to recent detections for stabilization
            recentDetections.add(contractNumber)

            // Check if we have a stable detection
            if (recentDetections.count { it == contractNumber } >= stabilizationThreshold) {
                stableContractNumber = contractNumber
                detectionCount = 0

                // Update UI
                binding.contractNumberText.text = getString(R.string.contract_number, contractNumber)
                binding.openButton.isEnabled = true
                binding.statusText.text = getString(R.string.contract_number, contractNumber)

                // Auto-open if enabled
                if (preferences.autoOpen) {
                    openBrowser(contractNumber)
                    recentDetections.clear()
                }
            } else {
                detectionCount++
                binding.statusText.text = "Обнаружен: $contractNumber (${recentDetections.count { it == contractNumber }}/$stabilizationThreshold)"
            }
        } else {
            recentDetections.clear()
            stableContractNumber = null
            binding.contractNumberText.text = getString(R.string.no_contract)
            binding.openButton.isEnabled = false
            binding.statusText.text = getString(R.string.scanning)
            binding.cameraOverlay.clearBoxes()
        }

        // Limit recent detections size
        if (recentDetections.size > 10) {
            recentDetections.clear()
        }
    }

    private fun openBrowser(contractNumber: String) {
        val urlTemplate = preferences.urlTemplate
        val url = urlTemplate.replace("{NUMBER}", Uri.encode(contractNumber))

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть ссылку: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Failed to open browser", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        textRecognizer.close()
    }
}
