package pt.droninho32.app

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import pt.droninho32.app.service.ScreenRecordService
import pt.droninho32.app.ui.components.LoadingBox
import pt.droninho32.app.ui.nav.AppNavHost
import pt.droninho32.app.ui.theme.Droninho32Theme
import pt.droninho32.app.viewmodel.ControlViewModel

/**
 * Única Activity da app (single-activity + Compose Navigation). Forçada a landscape
 * (manifesto). Cria o ControlViewModel partilhado e orquestra a gravação de tela
 * (consentimento de MediaProjection + ScreenRecordService).
 */
class MainActivity : ComponentActivity() {

    private val locator by lazy { Droninho32App.from(application).locator }

    // ViewModel partilhado por todos os ecrãs de controlo/mapa (owner = a Activity).
    private val controlVm: ControlViewModel by viewModels {
        ControlViewModel.Factory(
            drone = locator.droneRepository,
            backend = locator.backendRepository,
            settings = locator.settings,
            isLoggedInProvider = { locator.authRepository.cachedToken != null },
        )
    }

    private val projectionManager by lazy {
        getSystemService(MediaProjectionManager::class.java)
    }

    // Resultado do pedido de consentimento de captura de ecrã.
    private val mediaProjectionConsent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                val i = Intent(this, ScreenRecordService::class.java).apply {
                    action = ScreenRecordService.ACTION_START
                    putExtra(ScreenRecordService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenRecordService.EXTRA_DATA, data)
                }
                ContextCompat.startForegroundService(this, i)
            } else {
                controlVm.setScreenRecording(false)
            }
        }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Notificação do serviço em primeiro plano (Android 13+).
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Reflete o estado REAL da gravação de tela (serviço) no ViewModel.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ScreenRecordService.recordingState.collect { rec ->
                    controlVm.setScreenRecording(rec)
                }
            }
        }

        setContent {
            Droninho32Theme {
                // null = ainda a resolver; true/false = decidido.
                var startLoggedIn by remember { mutableStateOf<Boolean?>(null) }

                LaunchedEffect(Unit) {
                    locator.authRepository.warmUp()
                    startLoggedIn = locator.authRepository.isLoggedIn()
                }

                when (val logged = startLoggedIn) {
                    null -> LoadingBox()
                    else -> AppNavHost(
                        locator = locator,
                        controlVm = controlVm,
                        activityViewModelOwner = this@MainActivity,
                        startLoggedIn = logged,
                        onToggleScreenRecording = ::toggleScreenRecording,
                    )
                }
            }
        }
    }

    /** Alterna a gravação de tela: pede consentimento ou pára o serviço. */
    private fun toggleScreenRecording() {
        if (ScreenRecordService.recordingState.value) {
            startService(
                Intent(this, ScreenRecordService::class.java).apply {
                    action = ScreenRecordService.ACTION_STOP
                },
            )
        } else {
            mediaProjectionConsent.launch(projectionManager.createScreenCaptureIntent())
        }
    }
}
