package com.example.majes_alerta.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.majes_alerta.data.ApiClient
import com.example.majes_alerta.data.ApiRejected
import com.example.majes_alerta.data.LocalRepository
import com.example.majes_alerta.domain.AppState
import com.example.majes_alerta.domain.DraftReport
import com.example.majes_alerta.domain.Report
import com.example.majes_alerta.domain.ReportsDomain
import com.example.majes_alerta.domain.StatusHistoryItem
import com.example.majes_alerta.domain.SyncStatus
import com.example.majes_alerta.services.DeviceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // Evita que dos escrituras se pisen y que un reporte se envíe dos veces a la vez.
    private val stateMutex = Mutex()
    private val syncMutex = Mutex()

    init {
        loadData()
    }

    fun loadData() {
        _error.value = ""
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = repository.read() ?: repository.getInitialState()
                _uiState.value = state
                syncPending()
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al cargar datos locales."
            }
        }
    }

    private suspend fun update(transform: (AppState) -> AppState): AppState = stateMutex.withLock {
        val current = _uiState.value ?: repository.getInitialState()
        val next = transform(current)
        repository.write(next)
        _uiState.value = next
        next
    }

    private fun updateReport(id: String, transform: (Report) -> Report) {
        viewModelScope.launch(Dispatchers.IO) { updateReportNow(id, transform) }
    }

    private suspend fun updateReportNow(id: String, transform: (Report) -> Report) {
        update { st -> st.copy(reports = st.reports.map { if (it.id == id) transform(it) else it }) }
    }

    private fun commit(transform: (AppState) -> AppState) {
        viewModelScope.launch(Dispatchers.IO) { update(transform) }
    }

    fun apiUrl(): String = _uiState.value?.apiUrl ?: ReportsDomain.DEFAULT_API_URL

    fun onboard() {
        commit { it.copy(onboarded = true) }
    }

    fun saveDraft(draft: DraftReport) {
        commit { it.copy(draft = draft) }
    }

    /** Guarda el reporte en el celular y luego intenta enviarlo. Si no hay red, queda pendiente. */
    suspend fun submit(draft: DraftReport): String {
        return withContext(Dispatchers.IO) {
            val errors = ReportsDomain.validate(draft)
            if (errors.isNotEmpty()) {
                throw IllegalStateException(errors.joinToString("\n"))
            }

            val at = ReportsDomain.formatIso(System.currentTimeMillis())
            val id = "MJ-L-" + UUID.randomUUID().toString().take(6).uppercase()
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
                syncStatus = SyncStatus.PENDING,
                synthetic = true,
                valueBand = draft.valueBand ?: "sin_dato"
            )

            update { current ->
                current.copy(reports = listOf(created) + current.reports, draft = ReportsDomain.emptyDraft())
            }
            syncMutex.withLock { send(created) }
            id
        }
    }

    /** Reenvía todos los reportes que quedaron pendientes por falta de conexión. */
    suspend fun syncPending(): Int = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val pending = _uiState.value?.reports.orEmpty().filter { it.syncStatus == SyncStatus.PENDING }
            pending.forEach { send(it) }
            _uiState.value?.reports.orEmpty().count { it.syncStatus == SyncStatus.PENDING }
        }
    }

    fun syncPendingAsync() {
        viewModelScope.launch { syncPending() }
    }

    /** Reintento manual de un reporte pendiente o rechazado. */
    fun retry(reportId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            syncMutex.withLock {
                val r = _uiState.value?.reports?.find { it.id == reportId } ?: return@withLock
                if (r.syncStatus == SyncStatus.PENDING || r.syncStatus == SyncStatus.REJECTED) send(r)
            }
        }
    }

    private suspend fun send(r: Report) {
        // Si otra llamada ya lo envió, no repetir.
        val latest = _uiState.value?.reports?.find { it.id == r.id } ?: return
        if (latest.syncStatus == SyncStatus.SENT) return
        try {
            val photo = deviceService.jpegBase64(latest.photo)
            val created = ApiClient(apiUrl()).createReport(latest, photo)
            val at = ReportsDomain.formatIso(System.currentTimeMillis())
            val label = ApiClient.statusLabel(created.status)
            updateReportNow(r.id) {
                it.copy(
                    serverId = created.id,
                    trackingCode = created.tracking,
                    syncStatus = SyncStatus.SENT,
                    syncError = null,
                    status = label,
                    history = it.history + StatusHistoryItem("Enviado al servidor como ${created.id}", at)
                )
            }
        } catch (e: ApiRejected) {
            updateReportNow(r.id) { it.copy(syncStatus = SyncStatus.REJECTED, syncError = e.message) }
        } catch (e: Exception) {
            updateReportNow(r.id) {
                it.copy(syncStatus = SyncStatus.PENDING, syncError = "Sin conexión con ${apiUrl()}. Se reintentará.")
            }
        }
    }

    /** Consulta el estado real en el servidor con el código privado de seguimiento. */
    fun refreshStatus(reportId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = _uiState.value?.reports?.find { it.id == reportId } ?: return@launch
            val code = r.trackingCode ?: return@launch
            _isBusy.value = true
            try {
                val result = ApiClient(apiUrl()).track(code)
                updateReportNow(reportId) { it.copy(status = result.status, history = result.history, syncError = null) }
            } catch (e: Exception) {
                updateReportNow(reportId) { it.copy(syncError = "No se pudo actualizar: ${e.message}") }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun setApiUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        commit { it.copy(apiUrl = clean.ifBlank { null }) }
    }

    /** Devuelve un mensaje legible con el resultado de la prueba. */
    suspend fun testConnection(url: String): String = withContext(Dispatchers.IO) {
        val clean = url.trim().trimEnd('/')
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            return@withContext "La dirección debe empezar con http:// o https://"
        }
        try {
            if (ApiClient(clean).health()) "Conectado con $clean" else "El servidor respondió, pero no es Majes Alerta."
        } catch (e: Exception) {
            "No se pudo conectar con $clean. ¿Está corriendo server.py?"
        }
    }

    fun advance(reportId: String) {
        updateReport(reportId) { ReportsDomain.advance(it) }
    }

    fun resetData() {
        viewModelScope.launch(Dispatchers.IO) {
            val old = _uiState.value
            update { repository.getInitialState().copy(onboarded = true, apiUrl = it.apiUrl) }

            if (old != null) {
                val photos = old.reports.mapNotNull { it.photo } + listOfNotNull(old.draft.photo)
                photos.forEach { deviceService.removePhoto(it) }
            }
        }
    }
}
