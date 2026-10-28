package com.example.majes_alerta.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ReportsDomain {

    /** Qué o a quién afectó el hecho. Si fue hurto o robo lo determinan la violencia y las circunstancias. */
    val TYPES = listOf(
        "A una persona",
        "A un negocio",
        "A una vivienda",
        "Vehículo o autopartes"
    )
    val VEHICLE = TYPES[3]

    /** Nombres usados antes de incluir hurtos; se convierten al leer datos guardados. */
    val LEGACY_TYPES = mapOf(
        "Robo a persona" to TYPES[0],
        "Robo a negocio" to TYPES[1],
        "Robo a vivienda" to TYPES[2],
        "Robo de vehículo" to TYPES[3]
    )

    /** ¿Hubo violencia o amenaza? Distingue un hurto de un robo. Claves iguales a la API. */
    val FORCES = linkedMapOf(
        "sin_violencia" to "No, sin violencia ni amenaza",
        "amenaza" to "Me amenazaron",
        "violencia" to "Me agredieron"
    )

    /** Circunstancias que agravan un hurto o robo (Código Penal, arts. 186, 187 y 189). */
    val CIRCUMSTANCES = linkedMapOf(
        "dos_o_mas" to "Participaron dos o más personas",
        "casa_habitada" to "Dentro de una vivienda habitada",
        "destreza" to "Con destreza, escalamiento o rotura de puertas, ventanas o candados",
        "vehiculo_medio" to "Los autores usaron un vehículo (moto, auto)",
        "uso_momentaneo" to "El bien fue devuelto o abandonado tras usarlo"
    )

    /** Claves que espera la API web, en el mismo orden que TYPES. */
    val TYPE_KEYS = mapOf(
        TYPES[0] to "persona",
        TYPES[1] to "negocio",
        TYPES[2] to "vivienda",
        TYPES[3] to "vehiculo"
    )

    /** Valor aproximado de lo sustraído (clave de API → etiqueta). Igual que en landing_page/analytics.py. */
    val VALUE_BANDS = linkedMapOf(
        "sin_dato" to "No indicado",
        "bajo" to "Hasta S/ 535 (10% de la UIT)",
        "medio" to "S/ 536 a S/ 2 000",
        "alto" to "S/ 2 001 a S/ 10 000",
        "muy_alto" to "Más de S/ 10 000"
    )

    /** Estados del servidor → texto que muestra la app. */
    val SERVER_STATES = mapOf(
        "recibido" to "Recibido por el servidor",
        "validado" to "Validado",
        "cerrado" to "Cerrado",
        "duplicado" to "Duplicado",
        "descartado" to "Descartado"
    )

    const val DEFAULT_API_URL = "http://10.0.2.2:8000"

    val STATES = listOf(
        "Registrado localmente",
        "En revisión (simulada)",
        "Validado (simulado)",
        "Cerrado (simulado)"
    )

    val CENTER = ReportLocation(latitude = -16.357, longitude = -72.187)

    private fun getIsoFormat(): SimpleDateFormat {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format
    }

    fun formatIso(timestamp: Long): String {
        return getIsoFormat().format(Date(timestamp))
    }

    fun parseIso(isoStr: String): Long {
        if (isoStr.isBlank()) return 0L
        return try {
            getIsoFormat().parse(isoStr)?.time ?: 0L
        } catch (e: Exception) {
            try {
                // Fallback for simple ISO format without milliseconds
                val fallback = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                fallback.timeZone = TimeZone.getTimeZone("UTC")
                fallback.parse(isoStr)?.time ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }
    }

    fun emptyDraft(clock: Long = System.currentTimeMillis()): DraftReport {
        return DraftReport(
            type = TYPES[0],
            description = "",
            occurredAt = formatIso(clock),
            location = null,
            photo = null,
            weapon = false,
            injury = false,
            consent = false,
            valueBand = "sin_dato"
        )
    }

    fun validate(d: DraftReport, clock: Long = System.currentTimeMillis()): List<String> {
        val errors = mutableListOf<String>()
        if (d.type !in TYPES) {
            errors.add("Indica qué o a quién afectó el hecho.")
        }
        if (d.force !in FORCES) {
            errors.add("Indica si hubo violencia o amenaza.")
        }
        val descTrim = d.description.trim()
        if (descTrim.length < 12 || descTrim.length > 1200) {
            errors.add("Describe el hecho con 12 a 1200 caracteres.")
        }
        val t = parseIso(d.occurredAt)
        if (t <= 0L || t > clock + 120000L || t < clock - 366L * 86400000L) {
            errors.add("Indica una fecha pasada válida, dentro del último año.")
        }
        val p = d.location
        if (p == null || !p.latitude.isFinite() || !p.longitude.isFinite() ||
            Math.abs(p.latitude) > 90.0 || Math.abs(p.longitude) > 180.0
        ) {
            errors.add("Selecciona y confirma una ubicación.")
        } else if (!p.confirmed) {
            errors.add("Confirma que la ubicación corresponde al lugar del hecho.")
        }
        if (!d.consent) {
            errors.add("Confirma que es un reporte ficticio.")
        }
        return errors
    }

    fun distance(a: ReportLocation, b: ReportLocation): Double {
        val rad = { x: Double -> x * Math.PI / 180.0 }
        val p = rad(b.latitude - a.latitude)
        val q = rad(b.longitude - a.longitude)
        val sinP = Math.sin(p / 2.0)
        val sinQ = Math.sin(q / 2.0)
        val valA = Math.min(1.0, sinP * sinP + Math.cos(rad(a.latitude)) * Math.cos(rad(b.latitude)) * sinQ * sinQ)
        return 6371000.0 * 2.0 * Math.asin(Math.sqrt(valA))
    }

    fun dbscan(rows: List<Report>, eps: Double = 250.0, minPts: Int = 3): IntArray {
        val labels = IntArray(rows.size) { -2 } // -2: unvisited, -1: noise, >= 0: cluster
        var cluster = 0
        val ns = rows.map { a ->
            rows.indices.filter { i -> distance(a.location, rows[i].location) <= eps }
        }

        for (i in rows.indices) {
            if (labels[i] != -2) continue
            if (ns[i].size < minPts) {
                labels[i] = -1
                continue
            }
            labels[i] = cluster
            val queue = ArrayDeque(ns[i])
            val seen = HashSet(queue)
            while (queue.isNotEmpty()) {
                val j = queue.removeFirst()
                if (labels[j] == -1) labels[j] = cluster
                if (labels[j] != -2) continue
                labels[j] = cluster
                if (ns[j].size >= minPts) {
                    for (k in ns[j]) {
                        if (seen.add(k)) {
                            queue.add(k)
                        }
                    }
                }
            }
            cluster++
        }
        return labels
    }

    fun candidates(d: Report, rows: List<Report>): List<String> {
        return rows.filter { r ->
            r.id != d.id &&
                    r.type == d.type &&
                    distance(d.location, r.location) <= 100.0 &&
                    Math.abs(parseIso(d.occurredAt) - parseIso(r.occurredAt)) <= 1800000L
        }.map { it.id }
    }

    /** Un robo de vehículo sin valor indicado se asume al menos "alto" (misma regla que la web). */
    fun valueBand(type: String, band: String?): String {
        val b = if (band != null && band in VALUE_BANDS) band else "sin_dato"
        return if (type == VEHICLE && b in setOf("sin_dato", "bajo", "medio")) "alto" else b
    }

    /** Índice de daño: 1 base + valor + violencia o amenaza 2 + arma 3 + lesión 4. Igual que la web. */
    fun harm(r: Report): Int {
        val value = when (valueBand(r.type, r.valueBand)) {
            "medio" -> 1
            "alto" -> 3
            "muy_alto" -> 5
            else -> 0
        }
        val force = if (r.force == "amenaza" || r.force == "violencia") 2 else 0
        return 1 + value + force + (if (r.weapon) 3 else 0) + (if (r.injury) 4 else 0)
    }

    /** Motivos por los que un solo reporte es grave aunque no haya otros cerca. */
    fun criticalReasons(r: Report): List<String> {
        val reasons = mutableListOf<String>()
        if (r.injury) reasons.add("Lesión reportada")
        if (r.type == VEHICLE) reasons.add("Robo de vehículo")
        if (valueBand(r.type, r.valueBand) == "muy_alto" && r.type != VEHICLE) reasons.add("Pérdida mayor a S/ 10 000")
        if (reasons.isEmpty() && harm(r) >= 6) reasons.add("Índice de daño ≥ 6")
        return reasons
    }

    fun priority(d: Report, clock: Long = System.currentTimeMillis()): PriorityResult {
        val ageHours = (clock - parseIso(d.occurredAt)) / 3600000.0
        val recencyScore = if (ageHours <= 24.0) 15 else if (ageHours <= 168.0) 8 else 0
        val weaponScore = if (d.weapon) 30 else 0
        val injuryScore = if (d.injury) 15 else 0
        val valueScore = when (valueBand(d.type, d.valueBand)) {
            "medio" -> 5
            "alto" -> 10
            "muy_alto" -> 15
            else -> 0
        }

        val parts = listOf(
            "Base" to 10,
            "Arma declarada" to weaponScore,
            "Lesión declarada" to injuryScore,
            "Valor de lo sustraído" to valueScore,
            "Recencia" to recencyScore
        )
        val score = parts.sumOf { it.second }
        val label = if (score >= 60) "Alta" else if (score >= 30) "Media" else "Baja"
        return PriorityResult(score, parts, label)
    }

    fun advance(r: Report, clock: String = formatIso(System.currentTimeMillis())): Report {
        val index = STATES.indexOf(r.status)
        if (index < 0 || index >= STATES.size - 1) return r
        val nextStatus = STATES[index + 1]
        val newHistory = r.history + StatusHistoryItem(nextStatus, clock)
        return r.copy(status = nextStatus, history = newHistory)
    }

    fun sampleReports(clock: Long = System.currentTimeMillis()): List<Report> {
        return List(15) { i ->
            val at = formatIso(clock - (i + 1) * 7200000L)
            val id = "MJ-DEMO-${(i + 1).toString().padStart(3, '0')}"
            val statusIndex = i % 4
            val status = STATES[statusIndex]
            val historyItems = STATES.take(statusIndex + 1).map { s -> StatusHistoryItem(s, at) }
            val latOffset = if (i < 8) 0.0 else 0.009
            val lngOffset = if (i < 8) 0.0 else 0.008

            Report(
                id = id,
                type = TYPES[i % 4],
                description = "Caso ficticio para la presentación académica. No corresponde a un hecho real.",
                occurredAt = at,
                createdAt = at,
                location = ReportLocation(
                    latitude = CENTER.latitude + latOffset + (i % 4) * 0.00025,
                    longitude = CENTER.longitude + lngOffset + (i % 3) * 0.0002,
                    source = "demo",
                    confirmed = true
                ),
                photo = null,
                weapon = (i % 5 == 0),
                injury = false,
                status = status,
                history = historyItems,
                syncStatus = "local-only",
                synthetic = true
            )
        }
    }

    fun publicSummary(
        rows: List<Report>,
        days: Int = 30,
        typeFilter: String = "Todas",
        clock: Long = System.currentTimeMillis()
    ): PublicSummary {
        val selected = rows.filter { r ->
            (typeFilter == "Todas" || r.type == typeFilter) &&
                    (clock - parseIso(r.occurredAt) <= days * 86400000L) &&
                    (parseIso(r.occurredAt) <= clock) &&
                    STATES.indexOf(r.status) >= 2
        }

        val labels = dbscan(selected)
        val cellsMap = mutableMapOf<String, CellSummary>()
        for (r in selected) {
            val y = Math.floor(r.location.latitude / 0.006).toInt()
            val x = Math.floor(r.location.longitude / 0.006).toInt()
            val key = "$x:$y"
            val existing = cellsMap[key]
            if (existing == null) {
                cellsMap[key] = CellSummary(
                    latitude = (y + 0.5) * 0.006,
                    longitude = (x + 0.5) * 0.006,
                    count = 1
                )
            } else {
                cellsMap[key] = existing.copy(count = existing.count + 1)
            }
        }

        val visible = cellsMap.values.filter { it.count >= 3 }
        val clusterCount = labels.filter { it >= 0 }.distinct().size
        val hiddenCount = selected.size - visible.sumOf { it.count }

        return PublicSummary(
            total = selected.size,
            clusters = clusterCount,
            cells = visible,
            hidden = hiddenCount
        )
    }
}
