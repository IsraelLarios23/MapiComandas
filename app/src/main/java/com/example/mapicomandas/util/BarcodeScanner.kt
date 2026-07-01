package com.example.mapicomandas.util

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Escáner de código de barras con la cámara (ML Kit / Google Play Services).
 * Muestra la UI de cámara del sistema, maneja el permiso y devuelve el valor leído.
 */
object BarcodeScanner {

    fun escanear(
        context: Context,
        onResultado: (String) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_QR_CODE, Barcode.FORMAT_ITF
            )
            .enableAutoZoom()
            .build()
        val scanner = GmsBarcodeScanning.getClient(context, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                barcode.rawValue?.takeIf { it.isNotBlank() }?.let(onResultado)
            }
            .addOnCanceledListener { /* usuario cerró el escáner */ }
            .addOnFailureListener { e -> onError(e.message ?: "Error del escáner") }
    }
}
