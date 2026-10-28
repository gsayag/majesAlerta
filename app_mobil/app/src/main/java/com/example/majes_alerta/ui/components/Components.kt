package com.example.majes_alerta.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.majes_alerta.domain.CellSummary
import com.example.majes_alerta.domain.ReportLocation
import com.example.majes_alerta.ui.theme.AppBadgeBg
import com.example.majes_alerta.ui.theme.AppCardBg
import com.example.majes_alerta.ui.theme.AppGreen
import com.example.majes_alerta.ui.theme.AppInk
import com.example.majes_alerta.ui.theme.AppLime
import com.example.majes_alerta.ui.theme.AppLine
import com.example.majes_alerta.ui.theme.AppMetricBg
import com.example.majes_alerta.ui.theme.AppMuted
import com.example.majes_alerta.ui.theme.AppOrange
import com.example.majes_alerta.ui.theme.AppSecondaryButtonBg

@Composable
fun rememberBitmapFromUri(uriString: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(uriString) {
        if (uriString.isNull_or_empty()) null
        else try {
            val uri = Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bitmap?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}

private fun String?.isNull_or_empty(): Boolean = this == null || this.isBlank()

@Composable
fun AppButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    disabled: Boolean = false
) {
    val bgColor = if (secondary) AppSecondaryButtonBg else AppGreen
    val textColor = if (secondary) AppGreen else Color.White

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (disabled) bgColor.copy(alpha = 0.45f) else bgColor)
            .clickable(enabled = !disabled) { onClick() }
            .padding(vertical = 15.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
fun AppField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppInk,
            modifier = Modifier.padding(bottom = 7.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFBFCF8))
                .border(1.dp, AppLine, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    color = Color(0xFF8B9686),
                    fontSize = 15.sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                keyboardOptions = keyboardOptions,
                textStyle = TextStyle(
                    color = AppInk,
                    fontSize = 15.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AppCheck(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onValueChange(!value) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(23.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (value) AppGreen else Color.White)
                .border(1.dp, AppGreen, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (value) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = label,
            color = AppInk,
            fontSize = 13.sp,
            lineHeight = 21.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppChips(
    items: List<String>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { item ->
            val isSelected = (item == selectedValue)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) AppGreen else Color.White)
                    .border(1.dp, if (isSelected) AppGreen else AppLine, RoundedCornerShape(12.dp))
                    .clickable { onSelect(item) }
                    .padding(11.dp)
            ) {
                Text(
                    text = item,
                    fontSize = 12.sp,
                    color = if (isSelected) Color.White else AppInk
                )
            }
        }
    }
}

@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppBadgeBg)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = AppGreen,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun MetricCard(
    number: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AppMetricBg)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = number,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = AppGreen
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = AppMuted,
            lineHeight = 19.sp
        )
    }
}

@Composable
fun GeoGrid(
    points: List<CellSummary> = emptyList(),
    selectedLocation: ReportLocation? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFFE8EEDF))
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height

            // Draw grid lines
            val colStep = width / 8f
            val rowStep = height / 7f
            for (i in 0..8) {
                drawLine(
                    color = Color(0xFFD4DDCE),
                    start = Offset(i * colStep, 0f),
                    end = Offset(i * colStep, height),
                    strokeWidth = 1f
                )
            }
            for (i in 0..7) {
                drawLine(
                    color = Color(0xFFD4DDCE),
                    start = Offset(0f, i * rowStep),
                    end = Offset(width, i * rowStep),
                    strokeWidth = 1f
                )
            }

            val centerLat = -16.357
            val centerLng = -72.187

            if (selectedLocation != null) {
                val span = Math.max(
                    0.025,
                    Math.abs(selectedLocation.latitude - centerLat) * 3.0
                )
                val x = (width / 2f) + ((selectedLocation.longitude - centerLng) / span * (width * 0.4f)).toFloat()
                val y = (height / 2f) - ((selectedLocation.latitude - centerLat) / span * (height * 0.4f)).toFloat()

                drawCircle(
                    color = AppGreen,
                    radius = 8.dp.toPx(),
                    center = Offset(x, y)
                )
            } else if (points.isNotEmpty()) {
                val allLats = points.map { it.latitude }
                val allLngs = points.map { it.longitude }
                val avgLat = allLats.average()
                val avgLng = allLngs.average()
                val span = Math.max(
                    0.025,
                    Math.max(
                        allLats.maxOf { Math.abs(it - avgLat) } * 3.0,
                        allLngs.maxOf { Math.abs(it - avgLng) } * 3.0
                    )
                )

                points.forEach { p ->
                    val x = (width / 2f) + ((p.longitude - avgLng) / span * (width * 0.4f)).toFloat()
                    val y = (height / 2f) - ((p.latitude - avgLat) / span * (height * 0.4f)).toFloat()

                    // Draw aura rings
                    listOf(36.dp.toPx(), 27.dp.toPx(), 18.dp.toPx()).forEachIndexed { j, r ->
                        drawCircle(
                            color = AppOrange.copy(alpha = 0.08f + j * 0.09f),
                            radius = r,
                            center = Offset(x, y)
                        )
                    }

                    // Main dot
                    drawCircle(
                        color = AppOrange,
                        radius = 13.dp.toPx(),
                        center = Offset(x, y)
                    )

                    // Text count
                    drawContext.canvas.nativeCanvas.drawText(
                        p.count.toString(),
                        x,
                        y + 4.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 10.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                        }
                    )
                }
            } else {
                // Default center dot
                drawCircle(
                    color = AppGreen,
                    radius = 8.dp.toPx(),
                    center = Offset(width / 2f, height / 2f)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(13.dp)
        ) {
            Text(
                text = "UBICACIONES APROXIMADAS · DEMO",
                fontSize = 9.sp,
                color = AppGreen,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(13.dp)
        ) {
            Text(
                text = "Cuadrícula ilustrativa · no representa calles",
                fontSize = 8.sp,
                color = AppMuted
            )
        }
    }
}
