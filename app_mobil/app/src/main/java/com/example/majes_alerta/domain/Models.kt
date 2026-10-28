package com.example.majes_alerta.domain

data class ReportLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val source: String = "demo", // "gps", "manual", "demo"
    val confirmed: Boolean = false
)

data class ReportPhoto(
    val uri: String,
    val mimeType: String = "image/jpeg"
)

data class StatusHistoryItem(
    val status: String,
    val at: String
)

data class Report(
    val id: String,
    val type: String,
    val description: String,
    val occurredAt: String,
    val createdAt: String,
    val location: ReportLocation,
    val photo: ReportPhoto? = null,
    val weapon: Boolean = false,
    val injury: Boolean = false,
    val status: String,
    val history: List<StatusHistoryItem> = emptyList(),
    val syncStatus: String = "local-only",
    val synthetic: Boolean = true
)

data class DraftReport(
    val type: String = "Robo a persona",
    val description: String = "",
    val occurredAt: String = "",
    val location: ReportLocation? = null,
    val photo: ReportPhoto? = null,
    val weapon: Boolean = false,
    val injury: Boolean = false,
    val consent: Boolean = false
)

data class AppState(
    val version: Int = 1,
    val onboarded: Boolean = false,
    val reports: List<Report> = emptyList(),
    val draft: DraftReport = DraftReport()
)

data class PriorityResult(
    val score: Int,
    val parts: List<Pair<String, Int>>,
    val label: String
)

data class CellSummary(
    val latitude: Double,
    val longitude: Double,
    val count: Int
)

data class PublicSummary(
    val total: Int,
    val clusters: Int,
    val cells: List<CellSummary>,
    val hidden: Int
)
