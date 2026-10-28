package com.example.majes_alerta.services

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import com.example.majes_alerta.domain.ReportLocation
import com.example.majes_alerta.domain.ReportPhoto
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DeviceService(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun getLocation(): ReportLocation {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            throw IllegalStateException("Activa la ubicación del celular o usa el punto de demostración.")
        }

        val lastKnownGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val lastKnownNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val bestLastKnown = when {
            lastKnownGps != null && lastKnownNet != null -> if (lastKnownGps.time > lastKnownNet.time) lastKnownGps else lastKnownNet
            lastKnownGps != null -> lastKnownGps
            else -> lastKnownNet
        }

        if (bestLastKnown != null && (System.currentTimeMillis() - bestLastKnown.time) < 30000) {
            return ReportLocation(
                latitude = bestLastKnown.latitude,
                longitude = bestLastKnown.longitude,
                accuracy = bestLastKnown.accuracy,
                source = "gps",
                confirmed = false
            )
        }

        val freshLocation = withTimeoutOrNull(15000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }

                    override fun onProviderDisabled(provider: String) {}
                    override fun onProviderEnabled(provider: String) {}
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }

                val provider = if (isGpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
                try {
                    locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }

                cont.invokeOnCancellation {
                    locationManager.removeUpdates(listener)
                }
            }
        } ?: bestLastKnown

        if (freshLocation != null) {
            return ReportLocation(
                latitude = freshLocation.latitude,
                longitude = freshLocation.longitude,
                accuracy = freshLocation.accuracy,
                source = "gps",
                confirmed = false
            )
        } else {
            throw IllegalStateException("El GPS tardó demasiado. Reintenta en un lugar abierto o usa el modo demo.")
        }
    }

    fun savePhotoFromUri(inputUri: Uri): ReportPhoto {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(inputUri) ?: "image/jpeg"
        val ext = when (mimeType) {
            "image/png" -> "png"
            "image/heic" -> "heic"
            else -> "jpg"
        }

        val evidenceDir = File(context.filesDir, "evidence")
        if (!evidenceDir.exists()) {
            evidenceDir.mkdirs()
        }

        val destinationFile = File(evidenceDir, "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.$ext")

        val inputStream: InputStream = contentResolver.openInputStream(inputUri)
            ?: throw IllegalStateException("No se pudo leer la imagen seleccionada.")

        FileOutputStream(destinationFile).use { outputStream ->
            inputStream.copyTo(outputStream)
        }

        return ReportPhoto(
            uri = destinationFile.toURI().toString(),
            mimeType = mimeType
        )
    }

    /**
     * Prepara la foto para la API: JPEG, lado mayor ≤ 1280 px y menos de ~950 KB, en Base64 sin prefijo.
     * Devuelve null si no hay foto o no se pudo leer (el reporte se envía igual, sin imagen).
     */
    fun jpegBase64(photo: ReportPhoto?): String? {
        if (photo == null) return null
        return try {
            val path = Uri.parse(photo.uri).path ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
            val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            val scale = minOf(1f, 1280f / maxOf(decoded.width, decoded.height))
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            } else decoded
            var quality = 85
            var bytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bytes = out.toByteArray()
                quality -= 15
            } while (bytes.size > 950_000 && quality > 20)
            if (bytes.size > 950_000) null else Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun removePhoto(photo: ReportPhoto?) {
        if (photo == null) return
        try {
            val uri = Uri.parse(photo.uri)
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (file.exists() && file.absolutePath.contains("evidence")) {
                    file.delete()
                }
            }
        } catch (_: Exception) {
        }
    }
}
