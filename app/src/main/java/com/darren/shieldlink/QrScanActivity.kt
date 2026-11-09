package com.darren.shieldlink

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.net.wifi.WifiNetworkSuggestion
import android.provider.Settings
import android.os.Build

class QrScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var alreadyHandled = false // prevents scanning the same code repeatedly

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(this, "Camera permission is needed to scan QR codes", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scan)

        val toolbar = findViewById<Toolbar>(R.id.qrToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        previewView = findViewById(R.id.cameraPreview)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy -> analyzeFrame(imageProxy) }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't start camera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || alreadyHandled) {
            imageProxy.close()
            return
        }


        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    when (barcode.valueType) {
                        Barcode.TYPE_URL -> {
                            val url = barcode.url?.url ?: barcode.rawValue
                            if (!url.isNullOrBlank() && !alreadyHandled) {
                                alreadyHandled = true
                                checkScannedUrl(url)
                            }
                        }
                        Barcode.TYPE_WIFI -> {
                            val wifi = barcode.wifi
                            if (wifi != null && !alreadyHandled) {
                                alreadyHandled = true
                                handleWifiQr(wifi)
                            }
                        }
                        else -> { /* ignore other QR types for now */ }
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close() // must always close, even on failure
            }
    }

    private fun checkScannedUrl(url: String) {
        if (GamblingFilter.isGamblingDomain(this, url)) {
            val intent = Intent(this, WarningActivity::class.java).apply {
                putExtra(WarningActivity.EXTRA_URL, url)
                putExtra(WarningActivity.EXTRA_CATEGORY, "gambling")
                putExtra(WarningActivity.EXTRA_RETURN_HOME, true)
            }
            startActivity(intent)
            finish()
            return
        }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    VirusTotalClient.checkUrlDetailed(this@QrScanActivity, url)
                } catch (e: Exception) {
                    null
                }
            }

            if (result == null) {
                Toast.makeText(this@QrScanActivity, "Couldn't check the link", Toast.LENGTH_SHORT).show()
                alreadyHandled = false
                return@launch
            }

            val targetActivity = when (result.verdict) {
                LinkVerdict.MALICIOUS, LinkVerdict.RESTRICTED -> WarningActivity::class.java
                LinkVerdict.SAFE, LinkVerdict.UNKNOWN -> SafeActivity::class.java
            }

            val intent = Intent(this@QrScanActivity, targetActivity).apply {
                putExtra(WarningActivity.EXTRA_URL, url)
                putExtra(WarningActivity.EXTRA_RETURN_HOME, true)
                if (result.verdict == LinkVerdict.RESTRICTED) {
                    putExtra(WarningActivity.EXTRA_CATEGORY, "gambling")
                }
            }
            startActivity(intent)
            finish()
        }
    }
    private fun handleWifiQr(wifi: Barcode.WiFi) {
        val ssid = wifi.ssid
        val password = wifi.password
        if (ssid.isNullOrBlank()) {
            Toast.makeText(this, "Couldn't read the WiFi QR code", Toast.LENGTH_SHORT).show()
            alreadyHandled = false
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val suggestionBuilder = WifiNetworkSuggestion.Builder().setSsid(ssid)
            when (wifi.encryptionType) {
                Barcode.WiFi.TYPE_WPA -> suggestionBuilder.setWpa2Passphrase(password ?: "")
                Barcode.WiFi.TYPE_OPEN -> { /* no password needed */ }
                else -> suggestionBuilder.setWpa2Passphrase(password ?: "")
            }

            val suggestion = suggestionBuilder.build()
            val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS).apply {
                putParcelableArrayListExtra(
                    Settings.EXTRA_WIFI_NETWORK_LIST,
                    arrayListOf(suggestion)
                )
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Detected network: $ssid. Please connect manually in WiFi settings.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}