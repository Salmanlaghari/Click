package com.click.browser.ui.screens

import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.click.browser.engine.ModeTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * QR scanner (1.8.9 C6): a real CameraX + ML Kit scanner — the address-bar
 * button that used to be a "coming soon" toast (removed as a dead control)
 * is back with the actual stack behind it. The camera runs only while this
 * screen is open; the first QR value found is reported once via [onScanned]
 * and the caller routes it exactly like typed address-bar input.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun QrScannerScreen(
    theme: ModeTheme,
    onScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val cameraProviderRef = remember { arrayOfNulls<ProcessCameraProvider>(1) }

    LaunchedEffect(previewView) {
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
        val delivered = AtomicBoolean(false)
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
            } else {
                val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(input)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            val raw = barcode.rawValue
                            if (!raw.isNullOrBlank() && delivered.compareAndSet(false, true)) {
                                onScanned(raw)
                                break
                            }
                        }
                    }
                    .addOnCompleteListener { imageProxy.close() }
            }
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProviderRef[0] = provider
                val preview = Preview.Builder().build()
                preview.surfaceProvider = previewView.surfaceProvider
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (_: Exception) {
                // No usable camera — the user can still close the sheet.
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            try { cameraProviderRef[0]?.unbindAll() } catch (_: Exception) { }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Scan a QR code", color = Color.White, fontSize = 18.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Point the camera at a QR code — the link opens in Click.",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(28.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close scanner", tint = Color.White)
                }
            }
        }
    }
}
