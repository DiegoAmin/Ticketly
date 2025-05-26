package com.example.torniqueteapp

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QrScannerActivity : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var previewView: PreviewView
    private lateinit var btnLogout: Button
    private lateinit var tvScanResult: TextView

    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isAnalyzing = true

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setupQrScanner()
        } else {
            showErrorMessage("Se requiere permiso de cámara para escanear QR")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        database = FirebaseDatabase.getInstance()
        auth = FirebaseAuth.getInstance()
        cameraExecutor = Executors.newSingleThreadExecutor()

        previewView = findViewById(R.id.previewView)
        btnLogout = findViewById(R.id.btnLogout)
        tvScanResult = findViewById(R.id.tvScanResult)

        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            setupQrScanner()
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun setupQrScanner() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (exc: Exception) {
                Log.e(TAG, "Error al inicializar cámara", exc)
                showErrorMessage("Error al iniciar cámara: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    @OptIn(ExperimentalGetImage::class)
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (!isAnalyzing) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val image = imageProxy.image ?: return@setAnalyzer
                    val inputImage = InputImage.fromMediaImage(
                        image,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()

                    BarcodeScanning.getClient(options)
                        .process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            if (barcodes.isNotEmpty()) {
                                val qrCode = barcodes.first().rawValue
                                qrCode?.let { code ->
                                    isAnalyzing = false
                                    runOnUiThread {
                                        validateQrCode(code)
                                    }
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        } catch (exc: Exception) {
            Log.e(TAG, "Error al vincular casos de uso", exc)
            showErrorMessage("Error al iniciar cámara")
        }
    }

    private fun validateQrCode(qrCode: String) {
        Log.d(TAG, "----------------------------------------")
        Log.d(TAG, "QR escaneado (original): $qrCode")

        try {
            // Ahora buscamos directamente por el código original
            val qrRef = database.getReference("qr_codes").child(qrCode)
            Log.d(TAG, "Buscando en: /qr_codes/$qrCode")

            qrRef.get().addOnSuccessListener { snapshot ->
                Log.d(TAG, "Respuesta de Firebase: ${snapshot.value}")

                if (snapshot.exists()) {
                    Log.d(TAG, "QR encontrado en Firebase")
                    val status = snapshot.child("status").getValue(String::class.java)
                    Log.d(TAG, "Estado actual: $status")

                    when (status) {
                        "active" -> {
                            Log.d(TAG, "Actualizando estado a 'utilizado'")
                            val updates = hashMapOf<String, Any>(
                                "status" to "utilizado",
                                "usedAt" to ServerValue.TIMESTAMP,
                                "usedBy" to (auth.currentUser?.uid ?: "unknown")
                            )

                            qrRef.updateChildren(updates)
                                .addOnSuccessListener {
                                    Log.d(TAG, "QR actualizado correctamente")
                                    showSuccessMessage("Buen viaje")
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Error al actualizar QR", e)
                                    showErrorMessage("Error al actualizar estado del QR")
                                }
                        }
                        "utilizado" -> {
                            Log.d(TAG, "QR ya fue utilizado anteriormente")
                            showErrorMessage("QR utilizado, Genere un nuevo QR para ingresar")
                        }
                        else -> {
                            Log.d(TAG, "QR tiene estado inválido: $status")
                            showErrorMessage("QR no válido")
                        }
                    }
                } else {
                    Log.d(TAG, "QR NO encontrado en Firebase")
                    showErrorMessage("QR no registrado en el sistema")

                    // DEBUG: Mostrar estructura completa de /qrcodes
                    database.getReference("qr_codes").get()
                        .addOnSuccessListener { allQRCodes ->
                            Log.d(TAG, "Contenido completo de /qrcodes:")
                            allQRCodes.children.forEach { child ->
                                Log.d(TAG, "Clave: ${child.key} | Valor: ${child.value}")
                            }
                        }
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error al consultar Firebase", e)
                showErrorMessage("Error de conexión con la base de datos")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar QR", e)
            showErrorMessage("Formato de QR no válido")
        }
    }
    private fun showSuccessMessage(message: String) {
        tvScanResult.text = "✓ $message"
        //Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        resetScannerAfterDelay()
    }

    private fun showErrorMessage(message: String) {
        tvScanResult.text = "✗ $message"
        //Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        resetScannerAfterDelay()
    }

    private fun resetScannerAfterDelay() {
        previewView.postDelayed({
            isAnalyzing = true
            tvScanResult.text = "Escanea un código QR"
        }, 3000)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }

    companion object {
        private const val TAG = "QrScannerActivity"
    }
}