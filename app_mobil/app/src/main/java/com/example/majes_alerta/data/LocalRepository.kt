package com.example.majes_alerta.data

import android.content.Context
import com.example.majes_alerta.domain.AppState
import com.example.majes_alerta.domain.ReportsDomain
import com.google.gson.Gson

class LocalRepository(context: Context) {

    private val prefs = context.getSharedPreferences("majes_mobile_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val KEY = "@majes/mobile/v1"

    fun read(): AppState? {
        val raw = prefs.getString(KEY, null) ?: return null
        return try {
            val state = gson.fromJson(raw, AppState::class.java)
            if (state == null || state.version != 1) {
                throw IllegalStateException("No se pudo leer la versión de los datos locales.")
            }
            // Datos guardados antes de incluir hurtos usaban "Robo a persona", etc.
            val legacy = ReportsDomain.LEGACY_TYPES
            state.copy(
                reports = state.reports.map { r -> legacy[r.type]?.let { r.copy(type = it) } ?: r },
                draft = legacy[state.draft.type]?.let { state.draft.copy(type = it) } ?: state.draft
            )
        } catch (e: Exception) {
            null
        }
    }

    fun write(state: AppState) {
        val json = gson.toJson(state)
        prefs.edit().putString(KEY, json).apply()
    }

    fun getInitialState(): AppState {
        return AppState(
            version = 1,
            onboarded = false,
            reports = ReportsDomain.sampleReports(),
            draft = ReportsDomain.emptyDraft()
        )
    }
}
