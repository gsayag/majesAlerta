package com.example.majes_alerta

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.majes_alerta.ui.components.AppButton
import com.example.majes_alerta.ui.components.Badge
import com.example.majes_alerta.ui.screens.DetailScreen
import com.example.majes_alerta.ui.screens.HistoryScreen
import com.example.majes_alerta.ui.screens.HomeScreen
import com.example.majes_alerta.ui.screens.OnboardingScreen
import com.example.majes_alerta.ui.screens.PanoramaScreen
import com.example.majes_alerta.ui.screens.ReportScreen
import com.example.majes_alerta.ui.screens.SettingsScreen
import com.example.majes_alerta.ui.theme.AppErrorBg
import com.example.majes_alerta.ui.theme.AppErrorText
import com.example.majes_alerta.ui.theme.AppGreen
import com.example.majes_alerta.ui.theme.AppPaper
import com.example.majes_alerta.ui.theme.AppMuted
import com.example.majes_alerta.ui.theme.MAJES_ALERTATheme
import com.example.majes_alerta.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MAJES_ALERTATheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    val viewModel = remember { MainViewModel(context.applicationContext as Application) }
    val uiState by viewModel.uiState.collectAsState()
    val errorMessage by viewModel.error.collectAsState()

    var selectedTab by remember { mutableStateOf("Inicio") }
    var selectedReportId by remember { mutableStateOf<String?>(null) }
    var showReportExitDialog by remember { mutableStateOf(false) }

    val state = uiState

    BackHandler {
        if (selectedReportId != null) {
            selectedReportId = null
        } else if (selectedTab != "Inicio") {
            if (selectedTab == "Reportar") {
                showReportExitDialog = true
            } else {
                selectedTab = "Inicio"
            }
        }
    }

    if (showReportExitDialog) {
        AlertDialog(
            onDismissRequest = { showReportExitDialog = false },
            title = { Text("Salir del formulario") },
            text = { Text("Guarda el borrador antes de salir si quieres conservar los cambios.") },
            confirmButton = {
                TextButton(onClick = {
                    showReportExitDialog = false
                    selectedTab = "Inicio"
                }) {
                    Text("Salir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportExitDialog = false }) {
                    Text("Seguir")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppPaper,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppPaper)
                    .padding(horizontal = 22.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "◎ majes",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppGreen
                    )
                    Text(
                        text = "alerta",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Normal,
                        color = AppGreen
                    )
                }
                Badge(text = "DEMO LOCAL")
            }
        },
        bottomBar = {
            if (state?.onboarded == true && selectedReportId == null && selectedTab != "Reportar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(vertical = 8.dp, horizontal = 5.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "Inicio" to "⌂",
                        "Reportar" to "＋",
                        "Historial" to "≡",
                        "Panorama" to "◎",
                        "Ajustes" to "⚙"
                    ).forEach { (name, glyph) ->
                        val isSelected = (selectedTab == name)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = name }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = glyph,
                                fontSize = 23.sp,
                                color = if (isSelected) AppGreen else Color(0xFF9AA68F)
                            )
                            Text(
                                text = name,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) AppGreen else AppMuted
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (errorMessage.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No se pudo cargar", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppErrorBg)
                            .padding(14.dp)
                    ) {
                        Text(errorMessage, color = AppErrorText)
                    }
                    AppButton(title = "Reintentar", onClick = { viewModel.loadData() })
                }
            } else if (state == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = AppGreen)
                    Text(
                        text = "Cargando tu demo…",
                        fontSize = 12.sp,
                        color = AppMuted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else if (!state.onboarded) {
                OnboardingScreen(onEnter = { viewModel.onboard() })
            } else if (selectedReportId != null) {
                val chosen = state.reports.find { it.id == selectedReportId }
                if (chosen != null) {
                    DetailScreen(
                        report = chosen,
                        allReports = state.reports,
                        onBack = { selectedReportId = null },
                        onAdvance = { viewModel.advance(it) }
                    )
                } else {
                    selectedReportId = null
                }
            } else {
                when (selectedTab) {
                    "Inicio" -> HomeScreen(
                        reports = state.reports,
                        draft = state.draft,
                        onCreateReport = { selectedTab = "Reportar" },
                        onResumeDraft = { selectedTab = "Reportar" },
                        onSelectReport = { selectedReportId = it }
                    )
                    "Reportar" -> ReportScreen(
                        draft = state.draft,
                        onSaveDraft = { viewModel.saveDraft(it) },
                        onSubmit = { viewModel.submit(it) },
                        onDone = { newId ->
                            selectedReportId = newId
                            selectedTab = "Historial"
                        },
                        onBack = { selectedTab = "Inicio" },
                        deviceService = viewModel.deviceService
                    )
                    "Historial" -> HistoryScreen(
                        reports = state.reports,
                        onSelectReport = { selectedReportId = it }
                    )
                    "Panorama" -> PanoramaScreen(
                        reports = state.reports
                    )
                    "Ajustes" -> SettingsScreen(
                        onResetData = { viewModel.resetData() }
                    )
                }
            }
        }
    }
}
