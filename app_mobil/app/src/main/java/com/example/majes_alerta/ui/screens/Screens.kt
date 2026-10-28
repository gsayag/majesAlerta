package com.example.majes_alerta.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.majes_alerta.domain.DraftReport
import com.example.majes_alerta.domain.Report
import com.example.majes_alerta.domain.ReportLocation
import com.example.majes_alerta.domain.ReportPhoto
import com.example.majes_alerta.domain.ReportsDomain
import com.example.majes_alerta.domain.SyncStatus
import com.example.majes_alerta.services.DeviceService
import com.example.majes_alerta.ui.components.AppButton
import com.example.majes_alerta.ui.components.AppCheck
import com.example.majes_alerta.ui.components.AppChips
import com.example.majes_alerta.ui.components.AppField
import com.example.majes_alerta.ui.components.Badge
import com.example.majes_alerta.ui.components.GeoGrid
import com.example.majes_alerta.ui.components.MetricCard
import com.example.majes_alerta.ui.components.rememberBitmapFromUri
import com.example.majes_alerta.ui.theme.AppCardBg
import com.example.majes_alerta.ui.theme.AppErrorBg
import com.example.majes_alerta.ui.theme.AppErrorText
import com.example.majes_alerta.ui.theme.AppGreen
import com.example.majes_alerta.ui.theme.AppInk
import com.example.majes_alerta.ui.theme.AppLime
import com.example.majes_alerta.ui.theme.AppLine
import com.example.majes_alerta.ui.theme.AppMuted
import com.example.majes_alerta.ui.theme.AppSecondaryButtonBg
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDate(isoStr: String): String {
    if (isoStr.isBlank()) return ""
    return try {
        val timestamp = ReportsDomain.parseIso(isoStr)
        if (timestamp <= 0L) return isoStr
        val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        format.format(Date(timestamp))
    } catch (_: Exception) {
        isoStr
    }
}

private fun toLimaDate(isoStr: String): String {
    val t = ReportsDomain.parseIso(isoStr)
    val ts = if (t > 0L) t else System.currentTimeMillis()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return sdf.format(Date(ts))
}

private fun toLimaTime(isoStr: String): String {
    val t = ReportsDomain.parseIso(isoStr)
    val ts = if (t > 0L) t else System.currentTimeMillis()
    val sdf = SimpleDateFormat("HH:mm", Locale.US)
    return sdf.format(Date(ts))
}

@Composable
fun OnboardingScreen(onEnter: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "MAJES / PARTICIPACIÓN CIUDADANA",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = AppGreen,
            letterSpacing = 2.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(AppGreen)
                .padding(30.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(text = "◎", fontSize = 49.sp, color = AppLime)
                Text(
                    text = "Tu reporte.\nUna mirada\nal territorio.",
                    fontSize = 37.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Registra un caso de prueba, adjunta evidencia y conoce el recorrido de la información.",
                    color = Color(0xFFD3DFC7),
                    fontSize = 15.sp,
                    lineHeight = 24.sp
                )
            }
        }

        Text(
            text = "Demo móvil independiente",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = AppInk
        )

        Text(
            text = "Todo se guarda en este celular. No se envían reportes a la Policía, a Serenazgo ni a la landing.",
            fontSize = 14.sp,
            color = AppMuted,
            lineHeight = 22.sp
        )

        Text(
            text = "La ubicación y la cámara se solicitan al utilizarlas. Puedes presentar con los datos ficticios y el punto de prueba.",
            fontSize = 12.sp,
            color = AppMuted,
            lineHeight = 19.sp
        )

        AppButton(
            title = "Entrar al demo →",
            onClick = onEnter
        )
    }
}

@Composable
fun HomeScreen(
    reports: List<Report>,
    draft: DraftReport,
    onCreateReport: () -> Unit,
    onResumeDraft: () -> Unit,
    onSelectReport: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "MAJES, AREQUIPA",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = AppGreen,
            letterSpacing = 2.sp
        )

        Text(
            text = "Reportes que\norientan.",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = AppInk,
            letterSpacing = (-1).sp
        )

        Text(
            text = "Un espacio para registrar, organizar y comprender información del territorio.",
            fontSize = 14.sp,
            color = AppMuted,
            lineHeight = 22.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(AppGreen)
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    text = "DEMO ACADÉMICO · SOLO ROBOS",
                    color = AppLime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "¿Qué ocurrió?",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Describe el hecho, agrega una foto y confirma dónde ocurrió.",
                    color = Color(0xFFD0DEC6),
                    lineHeight = 22.sp
                )
                AppButton(
                    title = "＋ Crear reporte",
                    secondary = true,
                    onClick = onCreateReport
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                number = reports.size.toString(),
                label = "Registros locales",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                number = reports.count { it.status == ReportsDomain.STATES[0] }.toString(),
                label = "Sin revisión simulada",
                modifier = Modifier.weight(1f)
            )
        }

        if (draft.description.isNotBlank()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppLine),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Tienes un borrador",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppInk
                    )
                    Text(
                        text = draft.description,
                        fontSize = 12.sp,
                        color = AppMuted,
                        maxLines = 2
                    )
                    AppButton(
                        title = "Retomar borrador",
                        secondary = true,
                        onClick = onResumeDraft
                    )
                }
            }
        }

        Text(
            text = "Actividad reciente",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = AppInk
        )

        reports.take(3).forEach { report ->
            ReportCard(report = report, onClick = { onSelectReport(report.id) })
        }

        Text(
            text = "No es un servicio de emergencia. Usa únicamente información ficticia para la presentación.",
            fontSize = 12.sp,
            color = AppMuted,
            lineHeight = 19.sp
        )
    }
}

@Composable
fun ReportScreen(
    draft: DraftReport,
    onSaveDraft: (DraftReport) -> Unit,
    onSubmit: suspend (DraftReport) -> String,
    onDone: (String) -> Unit,
    onBack: () -> Unit,
    deviceService: DeviceService
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(0) }
    var currentDraft by remember { mutableStateOf(draft) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    var dateText by remember { mutableStateOf(toLimaDate(currentDraft.occurredAt)) }
    var timeText by remember { mutableStateOf(toLimaTime(currentDraft.occurredAt)) }
    var latText by remember { mutableStateOf("") }
    var lngText by remember { mutableStateOf("") }
    var showExitDialog by remember { mutableStateOf(false) }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun buildDatedDraft(): DraftReport {
        val validDate = Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(dateText)
        val validTime = Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(timeText)
        if (!validDate || !validTime) {
            throw IllegalStateException("Fecha inválida. Usa AAAA-MM-DD y hora HH:MM en Perú.")
        }
        val parsedTime = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("$dateText $timeText")?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
        if (parsedTime <= 0L) {
            throw IllegalStateException("Fecha u hora inválida.")
        }
        return currentDraft.copy(occurredAt = ReportsDomain.formatIso(parsedTime))
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val photo = deviceService.savePhotoFromUri(uri)
                currentDraft = currentDraft.copy(photo = photo)
            } catch (e: Exception) {
                error = e.message ?: "Error al procesar foto."
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraUri != null) {
            try {
                val photo = deviceService.savePhotoFromUri(tempCameraUri!!)
                currentDraft = currentDraft.copy(photo = photo)
            } catch (e: Exception) {
                error = e.message ?: "Error al procesar foto."
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            scope.launch {
                busy = true
                error = ""
                try {
                    val loc = deviceService.getLocation()
                    currentDraft = currentDraft.copy(location = loc)
                } catch (e: Exception) {
                    error = e.message ?: "Error de ubicación."
                } finally {
                    busy = false
                }
            }
        } else {
            error = "Permiso de ubicación denegado. Usa coordenadas manuales o el punto de demostración."
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                val file = File(context.cacheDir, "temp_photo_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                error = e.message ?: "Error al abrir la cámara."
            }
        } else {
            error = "Permiso de cámara denegado. Puedes continuar sin foto en este demo."
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Salir del formulario") },
            text = { Text("Los últimos cambios solo se conservarán si guardas el borrador.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onBack()
                }) {
                    Text("Salir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Seguir")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "NUEVO REPORTE / ${step + 1} DE 3",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = AppGreen,
            letterSpacing = 2.sp
        )

        Text(
            text = listOf("¿Qué ocurrió?", "Ubica el hecho", "Revisa y registra")[step],
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = AppInk,
            letterSpacing = (-1).sp
        )

        Text(
            text = "Solo robos · caso ficticio · guardado en este celular.",
            fontSize = 14.sp,
            color = AppMuted,
            lineHeight = 22.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (i in 0..2) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (i <= step) AppGreen else AppLine)
                )
            }
        }

        if (step == 0) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppLine),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Modalidad",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppInk
                    )
                    AppChips(
                        items = ReportsDomain.TYPES,
                        selectedValue = currentDraft.type,
                        onSelect = { currentDraft = currentDraft.copy(type = it) }
                    )

                    AppField(
                        label = "Descripción del hecho",
                        value = currentDraft.description,
                        onValueChange = { currentDraft = currentDraft.copy(description = it) },
                        placeholder = "Describe un caso ficticio. Evita nombres y datos personales.",
                        singleLine = false,
                        minLines = 4,
                        maxLines = 6
                    )

                    Text(
                        text = "${currentDraft.description.length}/1200 caracteres · mínimo 12",
                        fontSize = 12.sp,
                        color = AppMuted
                    )

                    AppField(
                        label = "Fecha del hecho · AAAA-MM-DD",
                        value = dateText,
                        onValueChange = { dateText = it },
                        placeholder = "2026-10-27"
                    )

                    AppField(
                        label = "Hora del hecho · HH:MM · Perú (UTC−5)",
                        value = timeText,
                        onValueChange = { timeText = it },
                        placeholder = "18:30"
                    )

                    AppCheck(
                        value = currentDraft.weapon,
                        onValueChange = { currentDraft = currentDraft.copy(weapon = it) },
                        label = "Se reportó el uso de un arma"
                    )

                    AppCheck(
                        value = currentDraft.injury,
                        onValueChange = { currentDraft = currentDraft.copy(injury = it) },
                        label = "Se reportó una lesión"
                    )

                    Text(
                        text = "Valor aproximado de lo robado",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppInk
                    )
                    AppChips(
                        items = ReportsDomain.VALUE_BANDS.values.toList(),
                        selectedValue = ReportsDomain.VALUE_BANDS[currentDraft.valueBand ?: "sin_dato"] ?: "No indicado",
                        onSelect = { label ->
                            val key = ReportsDomain.VALUE_BANDS.entries.first { it.value == label }.key
                            currentDraft = currentDraft.copy(valueBand = key)
                        }
                    )
                    Text(
                        text = "Ayuda a distinguir un robo menor de uno grave. Un robo de vehículo alerta aunque sea el único en la zona.",
                        fontSize = 12.sp,
                        color = AppMuted
                    )

                    Text(
                        text = "Foto de evidencia (opcional en el demo)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppInk
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppButton(
                            title = "Tomar foto",
                            disabled = busy,
                            onClick = { cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA) },
                            modifier = Modifier.weight(1f)
                        )
                        AppButton(
                            title = "Galería",
                            secondary = true,
                            disabled = busy,
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    currentDraft.photo?.let { photo ->
                        val bitmap = rememberBitmapFromUri(photo.uri)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Foto seleccionada",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        AppButton(
                            title = "Quitar foto",
                            secondary = true,
                            onClick = { currentDraft = currentDraft.copy(photo = null) }
                        )
                    }
                }
            }
        }

        if (step == 1) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppLine),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "El lugar importa",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppInk
                    )
                    Text(
                        text = "El GPS obtiene tu posición actual. Confirma o corrige las coordenadas si el robo ocurrió en otro lugar. No se realiza seguimiento en segundo plano.",
                        fontSize = 12.sp,
                        color = AppMuted,
                        lineHeight = 19.sp
                    )

                    GeoGrid(selectedLocation = currentDraft.location)

                    AppButton(
                        title = "Obtener mi ubicación GPS",
                        disabled = busy,
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )

                    AppButton(
                        title = "Usar punto ficticio de Majes",
                        secondary = true,
                        disabled = busy,
                        onClick = {
                            currentDraft = currentDraft.copy(
                                location = ReportsDomain.CENTER.copy(source = "demo", confirmed = false)
                            )
                        }
                    )

                    currentDraft.location?.let { loc ->
                        val srcName = if (loc.source == "gps") "GPS" else if (loc.source == "manual") "Manual" else "Punto ficticio"
                        val accText = loc.accuracy?.let { " · precisión ±${Math.round(it)} m" } ?: ""
                        Text(
                            text = "%.6f, %.6f · %s%s".format(Locale.US, loc.latitude, loc.longitude, srcName, accText),
                            fontSize = 12.sp,
                            color = AppMuted
                        )
                    }

                    Text(
                        text = "O ingresa coordenadas manualmente",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppInk
                    )

                    AppField(
                        label = "Latitud",
                        value = latText,
                        onValueChange = { latText = it },
                        placeholder = "-16.357",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    AppField(
                        label = "Longitud",
                        value = lngText,
                        onValueChange = { lngText = it },
                        placeholder = "-72.187",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    AppButton(
                        title = "Aplicar coordenadas",
                        secondary = true,
                        onClick = {
                            try {
                                val lat = latText.replace(',', '.').toDoubleOrNull()
                                val lng = lngText.replace(',', '.').toDoubleOrNull()
                                if (lat == null || lng == null || Math.abs(lat) > 90.0 || Math.abs(lng) > 180.0) {
                                    throw IllegalStateException("Coordenadas inválidas.")
                                }
                                currentDraft = currentDraft.copy(
                                    location = ReportLocation(lat, lng, source = "manual", confirmed = false)
                                )
                            } catch (e: Exception) {
                                error = e.message ?: "Coordenadas inválidas."
                            }
                        }
                    )

                    AppCheck(
                        value = currentDraft.location?.confirmed == true,
                        onValueChange = { confirmed ->
                            if (currentDraft.location == null) {
                                error = "Primero selecciona una ubicación."
                            } else {
                                currentDraft = currentDraft.copy(
                                    location = currentDraft.location!!.copy(confirmed = confirmed)
                                )
                            }
                        },
                        label = "Confirmo que este es el lugar del hecho de prueba"
                    )
                }
            }
        }

        if (step == 2) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppLine),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Badge(text = "SIN CONEXIÓN A LA MUNICIPALIDAD")

                    Text(
                        text = currentDraft.type,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppInk
                    )

                    Text(
                        text = currentDraft.description.ifBlank { "Sin descripción" },
                        fontSize = 14.sp,
                        color = AppMuted,
                        lineHeight = 22.sp
                    )

                    Text(
                        text = "$dateText · $timeText (Perú)",
                        fontSize = 12.sp,
                        color = AppMuted
                    )

                    Text(
                        text = "Ubicación: " + (currentDraft.location?.let { "%.5f, %.5f".format(Locale.US, it.latitude, it.longitude) } ?: "Pendiente"),
                        fontSize = 12.sp,
                        color = AppMuted
                    )

                    Text(
                        text = "Foto: ${if (currentDraft.photo != null) "adjunta" else "sin foto"} · Arma: ${if (currentDraft.weapon) "sí" else "no"} · Lesión: ${if (currentDraft.injury) "sí" else "no"}",
                        fontSize = 12.sp,
                        color = AppMuted
                    )

                    currentDraft.photo?.let { photo ->
                        val bitmap = rememberBitmapFromUri(photo.uri)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Foto evidencia",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    AppCheck(
                        value = currentDraft.consent,
                        onValueChange = { currentDraft = currentDraft.copy(consent = it) },
                        label = "Confirmo que los datos son ficticios y que este demo no genera una denuncia ni una alerta real."
                    )

                    AppButton(
                        title = if (busy) "Enviando…" else "Registrar y enviar reporte",
                        disabled = busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                error = ""
                                try {
                                    val d = buildDatedDraft()
                                    val newId = onSubmit(d)
                                    onDone(newId)
                                } catch (e: Exception) {
                                    error = e.message ?: "Error al registrar reporte."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    )
                }
            }
        }

        if (error.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppErrorBg)
                    .padding(14.dp)
            ) {
                Text(
                    text = error,
                    color = AppErrorText,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (step > 0) {
                AppButton(
                    title = "Anterior",
                    secondary = true,
                    disabled = busy,
                    onClick = { step-- },
                    modifier = Modifier.weight(1f)
                )
            }
            if (step < 2) {
                AppButton(
                    title = "Continuar →",
                    disabled = busy,
                    onClick = {
                        try {
                            val d = buildDatedDraft()
                            if (step == 0 && (d.description.isBlank() || d.description.trim().length < 12)) {
                                throw IllegalStateException("Completa la descripción antes de continuar.")
                            }
                            if (step == 1 && d.location?.confirmed != true) {
                                throw IllegalStateException("Selecciona y confirma la ubicación.")
                            }
                            onSaveDraft(d)
                            step++
                        } catch (e: Exception) {
                            error = e.message ?: "Completa los campos requeridos."
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        AppButton(
            title = "Guardar borrador",
            secondary = true,
            disabled = busy,
            onClick = {
                try {
                    val d = buildDatedDraft()
                    onSaveDraft(d)
                    error = ""
                } catch (e: Exception) {
                    error = e.message ?: "Error al guardar borrador."
                }
            }
        )

        AppButton(
            title = "Volver al inicio",
            secondary = true,
            disabled = busy,
            onClick = { showExitDialog = true }
        )
    }
}


@Composable
fun HistoryScreen(
    reports: List<Report>,
    onSelectReport: (String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var filterState by remember { mutableStateOf("Todos") }

    val filtered = reports.filter { r ->
        (filterState == "Todos" || r.status == filterState) &&
                "${r.id} ${r.type} ${r.description}".lowercase(Locale.getDefault())
                    .contains(search.lowercase(Locale.getDefault()))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "TU ACTIVIDAD",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = AppGreen,
            letterSpacing = 2.sp
        )

        Text(
            text = "Mis reportes",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = AppInk,
            letterSpacing = (-1).sp
        )

        AppField(
            label = "Buscar por código o descripción",
            value = search,
            onValueChange = { search = it },
            placeholder = "MJ-…"
        )

        AppChips(
            items = listOf("Todos") + ReportsDomain.STATES,
            selectedValue = filterState,
            onSelect = { filterState = it }
        )

        Text(
            text = "${filtered.size} resultados · datos guardados en este celular",
            fontSize = 12.sp,
            color = AppMuted
        )

        filtered.forEach { report ->
            ReportCard(report = report, onClick = { onSelectReport(report.id) })
        }

        if (filtered.isEmpty()) {
            Text(
                text = "No hay reportes con estos criterios.",
                fontSize = 14.sp,
                color = AppMuted
            )
        }
    }
}

@Composable
fun PanoramaScreen(reports: List<Report>) {
    var days by remember { mutableStateOf("30") }
    var kind by remember { mutableStateOf("Todas") }

    val summary = remember(reports, days, kind) {
        ReportsDomain.publicSummary(reports, days.toIntOrNull() ?: 30, kind)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "VISTA PREVIA DEL ANÁLISIS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = AppGreen,
            letterSpacing = 2.sp
        )

        Text(
            text = "Panorama local",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = AppInk,
            letterSpacing = (-1).sp
        )

        Text(
            text = "Ilustra cómo se aprovecharán los reportes en la web. Solo incluye estados validados o cerrados de forma simulada.",
            fontSize = 14.sp,
            color = AppMuted,
            lineHeight = 22.sp
        )

        AppChips(
            items = listOf("7", "30", "90"),
            selectedValue = days,
            onSelect = { days = it }
        )

        Text(
            text = "Periodo en días",
            fontSize = 12.sp,
            color = AppMuted
        )

        AppChips(
            items = listOf("Todas") + ReportsDomain.TYPES,
            selectedValue = kind,
            onSelect = { kind = it }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                number = summary.total.toString(),
                label = "Reportes elegibles",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                number = summary.clusters.toString(),
                label = "Agrupaciones DBSCAN",
                modifier = Modifier.weight(1f)
            )
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppLine),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Concentraciones de prueba",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppInk
                )

                GeoGrid(points = summary.cells)

                Text(
                    text = "Calor ilustrativo sobre conteos por celda. Se ocultan celdas con menos de 3 reportes: ${summary.hidden} reportes fuera del mapa.",
                    fontSize = 12.sp,
                    color = AppMuted,
                    lineHeight = 19.sp
                )

                summary.cells.forEachIndexed { i, c ->
                    Text(
                        text = "Celda ${i + 1}: ${c.count} reportes",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppInk
                    )
                }

                if (summary.cells.isEmpty()) {
                    Text(
                        text = "Sin celdas con suficiente información para mostrar.",
                        fontSize = 14.sp,
                        color = AppMuted
                    )
                }
            }
        }

        Text(
            text = "DBSCAN: distancia Haversine, radio 250 m y mínimo 3 puntos. Parámetros de demostración, pendientes de calibración. No predice delitos. El mapa es una cuadrícula aproximada, no cartografía de calles.",
            fontSize = 12.sp,
            color = AppMuted,
            lineHeight = 19.sp
        )
    }
}

@Composable
fun SettingsScreen(
    onResetData: () -> Unit,
    apiUrl: String = ReportsDomain.DEFAULT_API_URL,
    pendingCount: Int = 0,
    onSaveApiUrl: (String) -> Unit = {},
    onTestConnection: suspend (String) -> String = { "" },
    onSyncPending: suspend () -> Int = { 0 }
) {
    var showResetDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var urlText by remember(apiUrl) { mutableStateOf(apiUrl) }
    var connectionMessage by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Restablecer demo") },
            text = { Text("Se eliminarán los reportes, fotos vinculadas y el borrador de este demo en este dispositivo.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    onResetData()
                }) {
                    Text("Restablecer", color = AppErrorText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "ACERCA DEL PROTOTIPO",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = AppGreen,
            letterSpacing = 2.sp
        )

        Text(
            text = "Listo para crecer.",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = AppInk,
            letterSpacing = (-1).sp
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppLine),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Conexión con el demo web",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppInk
                )
                Text(
                    text = "Los reportes nuevos se envían a landing_page/server.py y aparecen en /gestion para su revisión. Emulador: http://10.0.2.2:8000. Celular físico: la IP de tu PC en la misma red Wi-Fi (inicia el servidor con --host 0.0.0.0).",
                    fontSize = 12.sp,
                    color = AppMuted,
                    lineHeight = 19.sp
                )
                AppField(
                    label = "Dirección del servidor",
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = ReportsDomain.DEFAULT_API_URL,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppButton(
                        title = if (testing) "Probando…" else "Guardar y probar",
                        disabled = testing,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSaveApiUrl(urlText)
                            scope.launch {
                                testing = true
                                connectionMessage = onTestConnection(urlText)
                                testing = false
                            }
                        }
                    )
                    AppButton(
                        title = "Enviar pendientes ($pendingCount)",
                        secondary = true,
                        disabled = testing || pendingCount == 0,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                testing = true
                                val left = onSyncPending()
                                connectionMessage = if (left == 0) "Todos los reportes fueron enviados." else "Quedan $left pendientes. Revisa la conexión."
                                testing = false
                            }
                        }
                    )
                }
                if (connectionMessage.isNotEmpty()) {
                    Text(text = connectionMessage, fontSize = 12.sp, color = AppInk)
                }

                Text(
                    text = "Arquitectura por capas",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppInk
                )
                Text(
                    text = "Pantallas → casos de uso → repositorio\nDominio: validación, estados y análisis\nServicios: GPS, cámara y archivos\nComposición: reemplazo de adaptadores",
                    fontSize = 12.sp,
                    color = AppMuted,
                    lineHeight = 19.sp
                )

                Text(
                    text = "Próxima conexión",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppInk
                )
                Text(
                    text = "Hoy el envío no usa cuentas ni idempotencia: si la red se corta justo después de que el servidor guarda, un reintento puede crear un duplicado (la web lo marca como posible coincidencia). Siguiente paso: autenticación y una clave de idempotencia por reporte.",
                    fontSize = 12.sp,
                    color = AppMuted,
                    lineHeight = 19.sp
                )
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppLine),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Recorrido para exponer",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppInk
                )
                Text(
                    text = "1. Iniciar server.py en la PC.\n2. Crear reporte con foto y valor de lo robado.\n3. Usar el punto ficticio y registrar.\n4. Abrir /gestion en la web y validar el caso.\n5. Volver a la app y tocar «Actualizar estado».\n6. Mostrar las alertas por gravedad en /gestion-datos.",
                    fontSize = 12.sp,
                    color = AppMuted,
                    lineHeight = 19.sp
                )
            }
        }

        AppButton(
            title = "Restablecer datos de demostración",
            secondary = true,
            onClick = { showResetDialog = true }
        )

        Text(
            text = "Desinstalar la app o borrar los datos puede eliminar los registros. Las evidencias del demo no tienen cifrado propio. Utiliza solamente datos ficticios.",
            fontSize = 12.sp,
            color = AppMuted,
            lineHeight = 19.sp
        )
    }
}

@Composable
fun DetailScreen(
    report: Report,
    allReports: List<Report>,
    onBack: () -> Unit,
    onAdvance: (String) -> Unit,
    onRetry: (String) -> Unit = {},
    onRefresh: (String) -> Unit = {},
    busy: Boolean = false
) {
    val context = LocalContext.current
    val p = remember(report) { ReportsDomain.priority(report) }
    val critical = remember(report) { ReportsDomain.criticalReasons(report) }
    val sent = report.syncStatus == SyncStatus.SENT
    val duplicates = remember(report, allReports) { ReportsDomain.candidates(report, allReports) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppButton(
            title = "← Volver",
            secondary = true,
            onClick = onBack
        )

        Text(
            text = if (sent) "SEGUIMIENTO EN EL SERVIDOR" else "SEGUIMIENTO LOCAL",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = AppGreen,
            letterSpacing = 2.sp
        )

        Text(
            text = report.type,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = AppInk,
            letterSpacing = (-1).sp
        )

        Badge(text = report.id)

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppLine),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = report.status,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppInk
                )
                Text(
                    text = when (report.syncStatus) {
                        SyncStatus.SENT -> "Enviado al servidor como ${report.serverId}"
                        SyncStatus.PENDING -> "Guardado en el celular · pendiente de envío"
                        SyncStatus.REJECTED -> "El servidor no aceptó el reporte"
                        else -> "Ejemplo local · no se envía"
                    },
                    fontSize = 12.sp,
                    color = AppMuted
                )
                report.syncError?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppErrorBg)
                            .padding(12.dp)
                    ) {
                        Text(text = msg, color = AppErrorText, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                report.trackingCode?.let { code ->
                    Text(
                        text = "Código privado de seguimiento:\n$code",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppInk
                    )
                }
                if (sent) {
                    AppButton(
                        title = if (busy) "Consultando…" else "Actualizar estado",
                        secondary = true,
                        disabled = busy,
                        onClick = { onRefresh(report.id) }
                    )
                }
                if (report.syncStatus == SyncStatus.PENDING || report.syncStatus == SyncStatus.REJECTED) {
                    AppButton(
                        title = "Reintentar envío",
                        secondary = true,
                        onClick = { onRetry(report.id) }
                    )
                }
                Text(
                    text = report.description,
                    fontSize = 14.sp,
                    color = AppMuted,
                    lineHeight = 22.sp
                )

                report.photo?.let { photo ->
                    val bitmap = rememberBitmapFromUri(photo.uri)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Foto del reporte",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Text(
                    text = "Hecho: ${formatDate(report.occurredAt)}\nRegistro: ${formatDate(report.createdAt)}",
                    fontSize = 12.sp,
                    color = AppMuted
                )

                Text(
                    text = "Ubicación: %.5f, %.5f · %s".format(Locale.US, report.location.latitude, report.location.longitude, report.location.source),
                    fontSize = 12.sp,
                    color = AppMuted
                )

                GeoGrid(selectedLocation = report.location)
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppLine),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Prioridad ${p.label.lowercase(Locale.getDefault())} · ${p.score}/85",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppInk
                )

                Text(
                    text = "Reglas ilustrativas para ordenar la revisión. No determina una respuesta de emergencia.",
                    fontSize = 12.sp,
                    color = AppMuted
                )

                Text(
                    text = "Valor de lo robado: ${ReportsDomain.VALUE_BANDS[ReportsDomain.valueBand(report.type, report.valueBand)]} · Índice de daño ${ReportsDomain.harm(report)}",
                    fontSize = 12.sp,
                    color = AppInk
                )

                if (critical.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppErrorBg)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Alerta por gravedad: ${critical.joinToString(" · ")}. Merece atención aunque no haya otros reportes cerca.",
                            color = AppErrorText,
                            fontSize = 12.sp,
                            lineHeight = 19.sp
                        )
                    }
                }

                p.parts.forEach { (key, score) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = key, fontSize = 12.sp, color = AppMuted)
                        Text(text = "+$score", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppInk)
                    }
                }

                if (duplicates.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppErrorBg)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Posibles coincidencias: ${duplicates.joinToString(", ")}. Misma modalidad, 100 m y 30 minutos. Requiere revisión; no se elimina automáticamente.",
                            color = AppErrorText,
                            fontSize = 12.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppLine),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Recorrido del reporte",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppInk
                )

                report.history.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(40.dp)
                                .background(AppLime)
                        )
                        Column {
                            Text(
                                text = "● ${item.status}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppInk
                            )
                            Text(
                                text = formatDate(item.at),
                                fontSize = 12.sp,
                                color = AppMuted
                            )
                        }
                    }
                }
            }
        }

        AppButton(
            title = "Compartir código y estado",
            secondary = true,
            onClick = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Majes Alerta DEMO\n${report.serverId ?: report.id}\n${report.status}" +
                            (report.trackingCode?.let { "\nCódigo: $it" } ?: "") +
                            "\nReporte ficticio, sin envío a autoridades."
                    )
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartir reporte"))
            }
        )

        if (!sent) Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppSecondaryButtonBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppLine),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Modo exposición",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppInk
                )

                Text(
                    text = "Este botón simula la revisión. En la versión real, un revisor autorizado cambiará el estado desde el servidor.",
                    fontSize = 12.sp,
                    color = AppMuted
                )

                val isLastStatus = report.status !in ReportsDomain.STATES || report.status == ReportsDomain.STATES[3]
                AppButton(
                    title = if (isLastStatus) "Recorrido completado" else "Simular siguiente estado",
                    disabled = isLastStatus,
                    onClick = { onAdvance(report.id) }
                )
            }
        }
    }
}

@Composable
fun ReportCard(
    report: Report,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppLine),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Badge(text = report.status)
            Text(
                text = report.type,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = AppInk
            )
            Text(
                text = "${report.id} · ${formatDate(report.occurredAt)}",
                fontSize = 12.sp,
                color = AppMuted
            )
            Text(
                text = report.description,
                fontSize = 12.sp,
                color = AppMuted,
                maxLines = 2
            )
            Text(
                text = "Ver seguimiento →",
                fontSize = 12.sp,
                color = AppGreen,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
