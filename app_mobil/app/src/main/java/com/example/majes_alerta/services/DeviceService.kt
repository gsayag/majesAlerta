package com.example.majes_alerta.services

import android.annotation.SuppressLint
import android.content.Context
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
