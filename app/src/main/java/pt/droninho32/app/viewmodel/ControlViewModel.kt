package pt.droninho32.app.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import pt.droninho32.app.data.dto.Command
import pt.droninho32.app.data.dto.FlightReq
import pt.droninho32.app.data.dto.TelemetryPoint
import pt.droninho32.app.data.repo.BackendRepository
import pt.droninho32.app.data.repo.DroneRepository
import pt.droninho32.app.data.store.JoystickCalibration
import pt.droninho32.app.data.store.SettingsStore
import pt.droninho32.app.domain.Outcome
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** Como a app está ligada (ou não) ao drone. */
enum class LinkState { UNKNOWN, CONNECTED, DISCONNECTED }

/** Estado do upload do voo no fim da sessão. */
enum class UploadState { IDLE, UPLOADING, DONE, SKIPPED, FAILED }

data class ControlUiState(
    val link: LinkState = LinkState.UNKNOWN,
    val telemetry: TelemetryPoint? = null,
    val armed: Boolean = false,
    val status: String = "idle",

    // --- Joysticks: posição CRUA normalizada [-1, 1] (centro 0; cima = +1). ---
    val leftX: Float = 0f,
    val leftY: Float = 0f,
    val rightX: Float = 0f,
    val rightY: Float = 0f,

    // --- Saídas calibradas enviadas por RC ao drone. ---
    val throttle: Int = 0,   // 0..100
    val yaw: Int = 0,        // -100..100
    val pitch: Int = 0,      // -100..100
    val roll: Int = 0,       // -100..100

    val calibration: JoystickCalibration = JoystickCalibration(),

    /** A gravar telemetria localmente (entre ARM e STOP/upload). */
    val telemetryRecording: Boolean = false,
    val recordedPoints: Int = 0,
    /** A gravar a tela (MediaProjection) — controlado pela Activity/serviço. */
    val screenRecording: Boolean = false,

    val upload: UploadState = UploadState.IDLE,
    val message: String? = null,
) {
    /** Última posição com fix de GPS, para o mapa centrar/seguir. */
    val livePosition: GeoPoint?
        get() = telemetry?.gps?.takeIf { it.fix }?.let { GeoPoint(it.lat, it.lon) }
}

/**
 * Coração do controlo em tempo real.
 *
 * - *Polling* a GET /api/telemetry a ~POLL_HZ e atualização do estado.
 * - Heartbeat a ~HEARTBEAT_HZ enquanto armado (satisfaz o failsafe do firmware).
 * - **Loop RC a 50 ms** (RC_HZ): enquanto armado, envia Command.rc(roll,pitch,yaw,throttle).
 * - Gravação do voo (telemetria) entre ARM e STOP e upload para o backend (se com sessão).
 *
 * Mapeamento dos joysticks (após aplicar a calibração):
 *  - Esquerdo:  Y → throttle 0..100 (empurrar para cima acelera; soltar/baixo = 0, SEGURO),
 *               X → yaw -100..100.
 *  - Direito:   Y → pitch -100..100 (cima = +),  X → roll -100..100 (direita = +).
 *
 * É retido ao nível da Activity para que ControlScreen e MapScreen partilhem a mesma
 * telemetria ao vivo.
 */
class ControlViewModel(
    private val drone: DroneRepository,
    private val backend: BackendRepository,
    private val settings: SettingsStore,
    private val isLoggedInProvider: () -> Boolean = { false },
) : ViewModel() {

    private val _state = MutableStateFlow(ControlUiState())
    val state: StateFlow<ControlUiState> = _state.asStateFlow()

    /** Drone selecionado para associar ao voo no backend (definido ao abrir o controlo). */
    private var droneId: Int? = null

    private var pollJob: Job? = null
    private var heartbeatJob: Job? = null
    private var rcJob: Job? = null

    /** Calibração (offsets de centro) carregada do DataStore. */
    private var cal = JoystickCalibration()

    /** Enquanto [SystemClock.elapsedRealtime] < este valor, o envio de RC fica bloqueado. */
    private var rcBlockedUntil: Long = 0L

    /** Buffer dos pontos gravados durante o voo atual. */
    private val recorded = mutableListOf<TelemetryPoint>()
    private var flightStartedIso: String? = null

    init {
        // Mantém a calibração sincronizada com o DataStore.
        viewModelScope.launch {
            settings.calibration.collect { c ->
                cal = c
                _state.update { it.copy(calibration = c) }
                recomputeOutputs()
            }
        }
    }

    /** Define qual o drone (do backend) a associar ao próximo voo gravado. */
    fun selectDrone(id: Int?) {
        droneId = id
    }

    // --- Ciclo de polling / heartbeat / RC (ligar quando o ecrã está visível) ---

    fun startLink() {
        if (pollJob?.isActive == true) return

        pollJob = viewModelScope.launch {
            while (isActive) {
                pollOnce()
                delay(POLL_INTERVAL_MS)
            }
        }
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                if (_state.value.armed) drone.sendCommand(Command.heartbeat())
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
        rcJob = viewModelScope.launch {
            while (isActive) {
                val s = _state.value
                val blocked = SystemClock.elapsedRealtime() < rcBlockedUntil
                // Segurança: só envia RC quando armado e fora do bloqueio pós-emergência.
                if (s.armed && !blocked) {
                    drone.sendCommand(Command.rc(s.roll, s.pitch, s.yaw, s.throttle))
                }
                delay(RC_INTERVAL_MS)
            }
        }
    }

    fun stopLink() {
        pollJob?.cancel(); pollJob = null
        heartbeatJob?.cancel(); heartbeatJob = null
        rcJob?.cancel(); rcJob = null
    }

    private suspend fun pollOnce() {
        when (val r = drone.getTelemetry()) {
            is Outcome.Ok -> {
                val t = r.value
                if (_state.value.telemetryRecording) recorded.add(t)
                _state.update {
                    it.copy(
                        link = LinkState.CONNECTED,
                        telemetry = t,
                        armed = t.armed,
                        status = t.status,
                        recordedPoints = recorded.size,
                    )
                }
            }
            is Outcome.Err -> _state.update { it.copy(link = LinkState.DISCONNECTED) }
        }
    }

    // --- Joysticks + calibração ---

    fun setLeftJoystick(x: Float, y: Float) {
        _state.update { it.copy(leftX = x, leftY = y) }
        recomputeOutputs()
    }

    fun setRightJoystick(x: Float, y: Float) {
        _state.update { it.copy(rightX = x, rightY = y) }
        recomputeOutputs()
    }

    /** Aplica os offsets de calibração às posições cruas e converte em roll/pitch/yaw/throttle. */
    private fun recomputeOutputs() {
        val s = _state.value
        val clx = (s.leftX - cal.leftX).coerceIn(-1f, 1f)
        val cly = (s.leftY - cal.leftY).coerceIn(-1f, 1f)
        val crx = (s.rightX - cal.rightX).coerceIn(-1f, 1f)
        val cry = (s.rightY - cal.rightY).coerceIn(-1f, 1f)

        // Throttle: metade superior do eixo Y esquerdo (soltar/baixo = 0). Cobre 0..100.
        val throttle = (cly.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)
        val yaw = (clx * 100f).roundToInt().coerceIn(-100, 100)
        val pitch = (cry * 100f).roundToInt().coerceIn(-100, 100)
        val roll = (crx * 100f).roundToInt().coerceIn(-100, 100)

        _state.update { it.copy(throttle = throttle, yaw = yaw, pitch = pitch, roll = roll) }
    }

    /** "Definir centro atual": as posições cruas atuais passam a ser o novo zero. */
    fun calibrateNeutral() {
        val s = _state.value
        val c = JoystickCalibration(s.leftX, s.leftY, s.rightX, s.rightY)
        cal = c
        _state.update { it.copy(calibration = c) }
        recomputeOutputs()
        viewModelScope.launch { settings.setCalibration(c) }
        _state.update { it.copy(message = MSG_CAL_SET) }
    }

    fun resetCalibration() {
        cal = JoystickCalibration()
        _state.update { it.copy(calibration = cal) }
        recomputeOutputs()
        viewModelScope.launch { settings.resetCalibration() }
        _state.update { it.copy(message = MSG_CAL_RESET) }
    }

    // --- Gravação de tela (estado reflectido a partir da Activity/serviço) ---

    fun setScreenRecording(recording: Boolean) {
        _state.update { it.copy(screenRecording = recording) }
    }

    // --- Comandos ---

    fun arm() {
        // Regra de segurança (contrato §7.3): só arma com throttle a 0.
        if (_state.value.throttle != 0) {
            _state.update { it.copy(message = MSG_ARM_REQUIRES_ZERO) }
            return
        }
        viewModelScope.launch {
            when (val r = drone.sendCommand(Command.arm())) {
                is Outcome.Ok -> {
                    startRecording()
                    _state.update { it.copy(armed = true, message = null) }
                }
                is Outcome.Err -> _state.update { it.copy(message = r.message) }
            }
        }
    }

    fun disarm() {
        viewModelScope.launch {
            drone.sendCommand(Command.disarm())
            _state.update { it.copy(armed = false) }
            zeroControls()
            finishFlightAndUpload()
        }
    }

    /** Paragem de emergência: corta tudo, zera os comandos e bloqueia o RC por alguns segundos. */
    fun emergencyStop() {
        rcBlockedUntil = SystemClock.elapsedRealtime() + EMERGENCY_BLOCK_MS
        viewModelScope.launch {
            drone.sendCommand(Command.stop())
            _state.update {
                it.copy(
                    armed = false,
                    leftX = 0f, leftY = 0f, rightX = 0f, rightY = 0f,
                    throttle = 0, yaw = 0, pitch = 0, roll = 0,
                    message = MSG_EMERGENCY,
                )
            }
            finishFlightAndUpload()
        }
    }

    private fun zeroControls() {
        _state.update {
            it.copy(
                leftX = 0f, leftY = 0f, rightX = 0f, rightY = 0f,
                throttle = 0, yaw = 0, pitch = 0, roll = 0,
            )
        }
    }

    // --- Gravação + upload do voo ---

    private fun startRecording() {
        recorded.clear()
        flightStartedIso = nowIso()
        _state.update { it.copy(telemetryRecording = true, recordedPoints = 0, upload = UploadState.IDLE) }
    }

    private suspend fun finishFlightAndUpload() {
        if (!_state.value.telemetryRecording) return
        val endedIso = nowIso()
        val points = recorded.toList()
        _state.update { it.copy(telemetryRecording = false) }

        if (points.isEmpty()) {
            _state.update { it.copy(upload = UploadState.SKIPPED) }
            return
        }
        if (!isLoggedInProvider()) {
            _state.update {
                it.copy(upload = UploadState.SKIPPED, message = "Voo gravado localmente (sem conta).")
            }
            return
        }

        _state.update { it.copy(upload = UploadState.UPLOADING) }
        val req = FlightReq(
            drone = droneId,
            startedAt = flightStartedIso,
            endedAt = endedIso,
            status = "completed",
        )
        when (backend.createFlightWithTelemetry(req, points)) {
            is Outcome.Ok -> _state.update {
                it.copy(upload = UploadState.DONE, message = "Voo enviado para o backend.")
            }
            is Outcome.Err -> _state.update {
                it.copy(upload = UploadState.FAILED, message = "Falha ao enviar o voo.")
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        stopLink()
        super.onCleared()
    }

    private fun nowIso(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    companion object {
        private const val POLL_HZ = 3
        private const val HEARTBEAT_HZ = 2
        const val POLL_INTERVAL_MS = (1000L / POLL_HZ)
        const val HEARTBEAT_INTERVAL_MS = (1000L / HEARTBEAT_HZ)

        /** Loop de RC a 50 ms (20 Hz). */
        const val RC_INTERVAL_MS = 50L

        /** Bloqueio de RC após paragem de emergência. */
        const val EMERGENCY_BLOCK_MS = 3000L

        private const val MSG_ARM_REQUIRES_ZERO = "Larga o acelerador (0) antes de armar."
        private const val MSG_EMERGENCY = "PARAGEM DE EMERGÊNCIA"
        private const val MSG_CAL_SET = "Centro dos joysticks definido."
        private const val MSG_CAL_RESET = "Calibração reposta."
    }

    class Factory(
        private val drone: DroneRepository,
        private val backend: BackendRepository,
        private val settings: SettingsStore,
        private val isLoggedInProvider: () -> Boolean,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ControlViewModel(drone, backend, settings, isLoggedInProvider) as T
        }
    }
}
