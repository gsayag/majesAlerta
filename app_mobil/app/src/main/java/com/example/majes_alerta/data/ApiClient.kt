package com.example.majes_alerta.data

import com.example.majes_alerta.domain.Report
import com.example.majes_alerta.domain.ReportsDomain
import com.example.majes_alerta.domain.StatusHistoryItem
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Error que devuelve el servidor (400, 404…). No se reintenta: hay que corregir el dato. */
class ApiRejected(message: String) : Exception(message)

data class CreatedReport(val id: String, val tracking: String, val status: String)

data class TrackResult(val status: String, val history: List<StatusHistoryItem>)

/**
 * Cliente de la API de landing_page/server.py (contrato en landing_page/docs/INTEGRACION.md).
 * Usa HttpURLConnection para no sumar dependencias. Llamar siempre fuera del hilo principal.
 */
class ApiClient(private val baseUrl: String) {

    private val gson = Gson()

    fun health(): Boolean {
        val json = request("GET", "/api/health", null)
        return json.get("ok")?.asBoolean == true
    }

    fun createReport(report: Report, photoBase64: String?): CreatedReport {
        val body = mapOf(
            "subtype" to (ReportsDomain.TYPE_KEYS[report.type] ?: throw ApiRejected("Modalidad no reconocida.")),
            "occurred_at" to toLimaIso(report.occurredAt),
            "description" to report.description.trim(),
            "lat" to report.location.latitude,
            "lng" to report.location.longitude,
            "weapon" to report.weapon,
            "injury" to report.injury,
            "value_band" to (report.valueBand ?: "sin_dato"),
            "consent" to true,
            "photo" to photoBase64
        )
        val json = request("POST", "/api/reports", gson.toJson(body))
        return CreatedReport(
            id = json.get("id").asString,
            tracking = json.get("tracking").asString,
            status = json.get("status").asString
        )
    }

    fun track(code: String): TrackResult {
        val json = request("POST", "/api/track", gson.toJson(mapOf("code" to code)))
        val history = json.getAsJsonArray("history").map {
            val item = it.asJsonObject
            StatusHistoryItem(statusLabel(item.get("status").asString), toUtcIso(item.get("timestamp").asString))
        }
        return TrackResult(statusLabel(json.get("status").asString), history)
    }

    private fun request(method: String, path: String, body: String?): JsonObject {
        val url = URL(baseUrl.trimEnd('/') + path)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = try {
                JsonParser.parseString(text).asJsonObject
            } catch (e: Exception) {
                throw IOException("Respuesta no válida del servidor ($code).")
            }
            if (code in 200..299) return json
            val message = json.get("error")?.asString ?: "El servidor respondió $code."
            // 5xx y 429 son temporales; el resto indica un dato que el servidor no acepta.
            if (code >= 500 || code == 429) throw IOException(message)
            throw ApiRejected(message)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun statusLabel(key: String): String = ReportsDomain.SERVER_STATES[key] ?: key

        /** La app guarda UTC con "Z"; el servidor (Python 3.10+) espera un desfase explícito. */
        fun toLimaIso(utcIso: String): String {
            val millis = ReportsDomain.parseIso(utcIso)
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            f.timeZone = TimeZone.getTimeZone("America/Lima")
            return f.format(Date(millis))
        }

        /** Fechas del servidor (con desfase) → formato UTC que usa la app. */
        fun toUtcIso(serverIso: String): String {
            return try {
                // Python envía microsegundos; SimpleDateFormat los leería como milisegundos.
                val noFraction = serverIso.replace(Regex("\\.\\d+"), "")
                val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(noFraction)
                ReportsDomain.formatIso(parsed!!.time)
            } catch (e: Exception) {
                serverIso
            }
        }
    }
}
