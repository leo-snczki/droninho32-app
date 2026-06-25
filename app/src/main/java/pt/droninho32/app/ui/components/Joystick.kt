package pt.droninho32.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * Joystick virtual de toque (área circular + knob arrastável).
 *
 * - O knob segue o dedo, limitado ao raio do círculo.
 * - Devolve, via [onMove], `x` e `y` normalizados em [-1, 1] (centro = 0).
 *   Convenção: `x` → direita = +1; `y` → **CIMA = +1** (invertido face às coordenadas de ecrã).
 * - Ao soltar (ou cancelar) regressa ao centro e emite (0, 0).
 * - Dimensão fixa em [size] → comporta-se bem em landscape.
 *
 * Não tem estado de negócio: o mapeamento para roll/pitch/yaw/throttle (e a calibração)
 * é feito no ControlViewModel.
 */
@Composable
fun Joystick(
    onMove: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    enabled: Boolean = true,
    baseColor: Color = Color(0xFF222C34),
    ringColor: Color = Color(0xFF3C4C59),
    knobColor: Color = Color(0xFF4FC3F7),
) {
    // Posição do knob em píxeis, relativa ao centro do círculo.
    var knob by remember { mutableStateOf(Offset.Zero) }
    val knobFraction = 0.30f

    Box(modifier = modifier.size(size)) {
        Canvas(
            modifier = Modifier
                .size(size)
                .then(
                    if (!enabled) Modifier
                    else Modifier.pointerInput(Unit) {
                        val side = minOf(this.size.width, this.size.height).toFloat()
                        val maxOffset = (side / 2f) * (1f - knobFraction)
                        val centerPx = Offset(this.size.width / 2f, this.size.height / 2f)

                        fun emit(rawFromCenter: Offset) {
                            val clamped = clampToRadius(rawFromCenter, maxOffset)
                            knob = clamped
                            val nx = (clamped.x / maxOffset).coerceIn(-1f, 1f)
                            val ny = (-clamped.y / maxOffset).coerceIn(-1f, 1f) // cima = +1
                            onMove(nx, ny)
                        }

                        detectDragGestures(
                            onDragStart = { pos -> emit(pos - centerPx) },
                            onDrag = { change, _ ->
                                change.consume()
                                emit(change.position - centerPx)
                            },
                            onDragEnd = { knob = Offset.Zero; onMove(0f, 0f) },
                            onDragCancel = { knob = Offset.Zero; onMove(0f, 0f) },
                        )
                    },
                ),
        ) {
            val r = this.size.minDimension / 2f * 0.98f
            val knobR = r * knobFraction
            // Base + anel
            drawCircle(color = baseColor, radius = r, center = center)
            drawCircle(color = ringColor, radius = r, center = center, style = Stroke(width = r * 0.05f))
            // Eixos (cruz ténue)
            drawLine(ringColor, Offset(center.x - r, center.y), Offset(center.x + r, center.y), strokeWidth = 2f)
            drawLine(ringColor, Offset(center.x, center.y - r), Offset(center.x, center.y + r), strokeWidth = 2f)
            // Knob
            drawCircle(
                color = knobColor.copy(alpha = if (enabled) 1f else 0.35f),
                radius = knobR,
                center = center + knob,
            )
        }
    }
}

/** Limita um vetor (a partir do centro) ao raio máximo, preservando a direção. */
private fun clampToRadius(p: Offset, maxRadius: Float): Offset {
    val dist = hypot(p.x.toDouble(), p.y.toDouble()).toFloat()
    if (dist <= maxRadius || dist == 0f) return p
    val scale = maxRadius / dist
    return Offset(p.x * scale, p.y * scale)
}
