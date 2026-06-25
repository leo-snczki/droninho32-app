package pt.droninho32.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pt.droninho32.app.R
import pt.droninho32.app.data.dto.TelemetryPoint
import pt.droninho32.app.ui.components.Joystick
import pt.droninho32.app.ui.components.LabelValueRow
import pt.droninho32.app.ui.components.SectionCard
import pt.droninho32.app.ui.components.StatusPill
import pt.droninho32.app.ui.theme.DnAmber
import pt.droninho32.app.ui.theme.DnGreen
import pt.droninho32.app.ui.theme.DnRed
import pt.droninho32.app.util.ScreenCapture
import pt.droninho32.app.viewmodel.ControlViewModel
import pt.droninho32.app.viewmodel.LinkState

/**
 * Ecrã de controlo em paisagem: dois joysticks virtuais (esquerdo = throttle/yaw,
 * direito = pitch/roll), valores em tempo real, ARMAR/DESARMAR/PARAGEM, e os botões
 * Print (captura), Gravar tela (MediaProjection) e Calibrar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(
    vm: ControlViewModel,
    onOpenMap: () -> Unit,
    onToggleScreenRecording: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val context = LocalContext.current

    var calibrating by remember { mutableStateOf(false) }

    // Mensagens pré-resolvidas (não se pode chamar stringResource fora de @Composable).
    val shotSaved = stringResource(R.string.shot_saved)
    val shotFailed = stringResource(R.string.shot_failed)

    LaunchedEffect(Unit) { vm.startLink() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            // -------- Barra superior compacta --------
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.control_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (state.link) {
                        LinkState.CONNECTED -> StatusPill(stringResource(R.string.control_connection_ok), DnGreen, Icons.Default.Wifi)
                        else -> StatusPill(stringResource(R.string.control_connection_fail), DnRed, Icons.Default.WifiOff)
                    }
                    if (state.armed) {
                        StatusPill(stringResource(R.string.control_armed), DnAmber, Icons.Default.LockOpen)
                    } else {
                        StatusPill(stringResource(R.string.control_disarmed), DnGreen, Icons.Default.Lock)
                    }
                    if (state.telemetryRecording) StatusPill("REC ${state.recordedPoints}", DnRed)
                    if (state.screenRecording) StatusPill("● TELA", DnRed)
                    OutlinedButton(onClick = onOpenMap) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.control_open_map))
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // -------- Linha principal: joystick | centro | joystick --------
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Joystick esquerdo (throttle / yaw)
                StickColumn(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.control_left_stick),
                    readout = "${stringResource(R.string.ctrl_throttle)} ${state.throttle}%  ·  ${stringResource(R.string.ctrl_yaw)} ${signed(state.yaw)}",
                ) {
                    Joystick(onMove = { x, y -> vm.setLeftJoystick(x, y) })
                }

                // Centro
                Column(
                    Modifier.weight(1.5f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LiveValues(state.throttle, state.yaw, state.pitch, state.roll)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { vm.arm() },
                            enabled = !state.armed && state.link == LinkState.CONNECTED && state.throttle == 0,
                            colors = ButtonDefaults.buttonColors(containerColor = DnGreen),
                            modifier = Modifier.weight(1f).height(50.dp),
                        ) { Text(stringResource(R.string.control_arm), fontWeight = FontWeight.Bold) }

                        Button(
                            onClick = { vm.disarm() },
                            enabled = state.armed,
                            colors = ButtonDefaults.buttonColors(containerColor = DnAmber),
                            modifier = Modifier.weight(1f).height(50.dp),
                        ) { Text(stringResource(R.string.control_disarm), fontWeight = FontWeight.Bold) }
                    }

                    // PARAGEM DE EMERGÊNCIA (sempre visível)
                    Button(
                        onClick = { vm.emergencyStop() },
                        colors = ButtonDefaults.buttonColors(containerColor = DnRed, contentColor = Color.White),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.control_stop), fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }

                    if (!state.armed) {
                        Text(
                            stringResource(R.string.control_arm_requires_zero),
                            style = MaterialTheme.typography.labelMedium,
                            color = DnAmber,
                        )
                    }

                    if (calibrating) {
                        CalibrationPanel(
                            vm = vm,
                            onClose = { calibrating = false },
                        )
                    } else {
                        // Botões de ação
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.PhotoCamera,
                                label = stringResource(R.string.ctrl_print),
                            ) {
                                scope.launch {
                                    val ok = ScreenCapture.captureView(context, view.rootView)
                                    snackbar.showSnackbar(if (ok) shotSaved else shotFailed)
                                }
                            }
                            ActionButton(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Videocam,
                                label = if (state.screenRecording)
                                    stringResource(R.string.ctrl_record_screen_stop)
                                else stringResource(R.string.ctrl_record_screen),
                                tint = if (state.screenRecording) DnRed else null,
                            ) { onToggleScreenRecording() }
                            ActionButton(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Tune,
                                label = stringResource(R.string.ctrl_calibrate),
                            ) { calibrating = true }
                        }

                        CompactTelemetry(state.telemetry)
                    }
                }

                // Joystick direito (pitch / roll)
                StickColumn(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.control_right_stick),
                    readout = "${stringResource(R.string.ctrl_pitch)} ${signed(state.pitch)}  ·  ${stringResource(R.string.ctrl_roll)} ${signed(state.roll)}",
                ) {
                    Joystick(onMove = { x, y -> vm.setRightJoystick(x, y) })
                }
            }
        }
    }
}

/** Coluna de um joystick: título em cima, knob ao centro, leitura em baixo. */
@Composable
private fun StickColumn(
    modifier: Modifier,
    title: String,
    readout: String,
    joystick: @Composable () -> Unit,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        joystick()
        Text(readout, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** Quatro valores ao vivo: throttle / yaw / pitch / roll. */
@Composable
private fun LiveValues(throttle: Int, yaw: Int, pitch: Int, roll: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ValueChip(Modifier.weight(1f), stringResource(R.string.ctrl_throttle), "$throttle%", DnGreen)
        ValueChip(Modifier.weight(1f), stringResource(R.string.ctrl_yaw), signed(yaw), MaterialTheme.colorScheme.primary)
        ValueChip(Modifier.weight(1f), stringResource(R.string.ctrl_pitch), signed(pitch), MaterialTheme.colorScheme.primary)
        ValueChip(Modifier.weight(1f), stringResource(R.string.ctrl_roll), signed(roll), MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ValueChip(modifier: Modifier, label: String, value: String, color: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Column(
            Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp)) {
        Icon(icon, contentDescription = null, tint = tint ?: androidx.compose.material3.LocalContentColor.current, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}

/** Painel de calibração: mostra valores crus, calibrados e offsets; permite definir/repor. */
@Composable
private fun CalibrationPanel(vm: ControlViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = state.calibration
    SectionCard(title = stringResource(R.string.cal_title)) {
        Text(stringResource(R.string.cal_explain), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        LabelValueRow(
            stringResource(R.string.cal_raw),
            "%.2f / %.2f · %.2f / %.2f".format(state.leftX, state.leftY, state.rightX, state.rightY),
        )
        LabelValueRow(
            stringResource(R.string.cal_calibrated),
            "${state.throttle}% / ${signed(state.yaw)} / ${signed(state.pitch)} / ${signed(state.roll)}",
        )
        LabelValueRow(
            stringResource(R.string.cal_offsets),
            "%.2f / %.2f · %.2f / %.2f".format(c.leftX, c.leftY, c.rightX, c.rightY),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.calibrateNeutral() }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cal_set_center), maxLines = 1)
            }
            OutlinedButton(onClick = { vm.resetCalibration() }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cal_reset), maxLines = 1)
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cal_close))
        }
    }
}

/** Telemetria compacta (mantém a informação essencial visível no ecrã de controlo). */
@Composable
private fun CompactTelemetry(t: TelemetryPoint?) {
    SectionCard(title = stringResource(R.string.control_status)) {
        if (t == null) {
            Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.loading))
            }
            return@SectionCard
        }
        LabelValueRow(stringResource(R.string.flight_status), t.status)
        LabelValueRow(stringResource(R.string.tel_motors), t.motors.joinToString(" / ") { "$it%" })
        LabelValueRow(
            stringResource(R.string.tel_battery),
            "%.2f V (%d%%)".format(t.battery.voltage, t.battery.percent),
        )
        LabelValueRow(
            stringResource(R.string.tel_gps),
            if (t.gps.fix) "%.5f, %.5f".format(t.gps.lat, t.gps.lon) else stringResource(R.string.tel_gps_nofix),
        )
        LabelValueRow(stringResource(R.string.tel_rssi), "${t.rssi} dBm")
    }
}

/** Formata um inteiro com sinal explícito (+/-). */
private fun signed(v: Int): String = if (v > 0) "+$v" else v.toString()
