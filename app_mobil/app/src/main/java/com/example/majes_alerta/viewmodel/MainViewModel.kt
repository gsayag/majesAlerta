package com.example.majes_alerta.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.majes_alerta.data.LocalRepository
import com.example.majes_alerta.domain.AppState
import com.example.majes_alerta.domain.DraftReport
import com.example.majes_alerta.domain.Report
import com.example.majes_alerta.domain.ReportsDomain
import com.example.majes_alerta.domain.StatusHistoryItem
import com.example.majes_alerta.services.DeviceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LocalRepository(application)
    val deviceService = DeviceService(application)

    private val _uiState = MutableStateFlow<AppState?>(null)
    val uiState: StateFlow<AppState?> = _uiState.asStateFlow()

    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        _error.value = ""
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = repository.read() ?: repository.getInitialState()
                _uiState.value = state
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al cargar datos locales."
            }
        }
    }

    private fun commit(transform: (AppState) -> AppState) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _uiState.value ?: repository.getInitialState()
            val next = transform(current)
            repository.write(next)
            _uiState.value = next
        }
    }

    fun onboard() {
        commit { it.copy(onboarded = true) }
    }

    fun saveDraft(draft: DraftReport) {
        commit { it.copy(draft = draft) }
    }

    suspend fun submit(draft: DraftReport): String {
        return withContext(Dispatchers.IO) {
            val errors = ReportsDomain.validate(draft)
            if (errors.isNotEmpty()) {
                throw IllegalStateException(errors.joinToString("\n"))
            }

            val at = ReportsDomain.formatIso(System.currentTimeMillis())
            val id = "MJ-" + Long.MAX_VALUE.toString(36).uppercase().takeLast(4) + "-" + UUID.randomUUID().toString().take(4).uppercase()
            val status = ReportsDomain.STATES[0]
            val created = Report(
                id = id,
                type = draft.type,
                description = draft.description,
                occurredAt = draft.occurredAt,
                createdAt = at,
                location = draft.location!!,
                photo = draft.photo,
                weapon = draft.weapon,
                injury = draft.injury,
                status = status,
                history = listOf(StatusHistoryItem(status, at)),
                syncStatus = "local-only",
                synthetic = true
            )

            val current = _uiState.value ?: repository.getInitialState()
            val next = current.copy(
                reports = listOf(created) + current.reports,
                draft = ReportsDomain.emptyDraft()
            )
            repository.write(next)
            _uiState.value = next
            id
        }
    }

    fun advance(reportId: String) {
        commit { current ->
            val updatedReports = current.reports.map { r ->
                if (r.id == reportId) ReportsDomain.advance(r) else r
            }
            current.copy(reports = updatedReports)
        }
    }

    fun resetData() {
        viewModelScope.launch(Dispatchers.IO) {
            val old = _uiState.value
            val next = repository.getInitialState().copy(onboarded = true)
            repository.write(next)
            _uiState.value = next

            if (old != null) {
                val photos = old.reports.mapNotNull { it.photo } + listOfNotNull(old.draft.photo)
                photos.forEach { deviceService.removePhoto(it) }
            }
        }
    }
}
