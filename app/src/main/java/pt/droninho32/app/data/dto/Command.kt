package pt.droninho32.app.data.dto

import com.squareup.moshi.JsonClass

/**
 * Corpo de POST /api/command (secção 3 do contrato). O campo [cmd] é obrigatório;
 * os restantes campos são opcionais (null) e só são serializados quando preenchidos —
 * o Moshi omite campos null por omissão, por isso o JSON enviado contém apenas o
 * essencial para cada comando.
 *
 * Comandos suportados pelo firmware:
 *  - "arm" / "disarm" / "stop" / "heartbeat"
 *  - "throttle" (value 0..100)
 *  - "motor" (motor 0..3, value 0..100)
 *  - "rc" (roll/pitch/yaw -100..100, throttle 0..100)
 */
@JsonClass(generateAdapter = true)
data class Command(
    val cmd: String,
    val value: Int? = null,
    val motor: Int? = null,
    val roll: Int? = null,
    val pitch: Int? = null,
    val yaw: Int? = null,
    val throttle: Int? = null,
) {
    companion object {
        fun arm() = Command(cmd = "arm")
        fun disarm() = Command(cmd = "disarm")
        fun stop() = Command(cmd = "stop")
        fun heartbeat() = Command(cmd = "heartbeat")
        fun throttle(value: Int) = Command(cmd = "throttle", value = value.coerceIn(0, 100))
        fun motor(index: Int, value: Int) =
            Command(cmd = "motor", motor = index.coerceIn(0, 3), value = value.coerceIn(0, 100))

        fun rc(roll: Int, pitch: Int, yaw: Int, throttle: Int) = Command(
            cmd = "rc",
            roll = roll.coerceIn(-100, 100),
            pitch = pitch.coerceIn(-100, 100),
            yaw = yaw.coerceIn(-100, 100),
            throttle = throttle.coerceIn(0, 100),
        )
    }
}

/** Resposta de POST /api/command. */
@JsonClass(generateAdapter = true)
data class CommandRes(
    val ok: Boolean = false,
    val status: String = "",
)

/** Resposta de GET /api/status. */
@JsonClass(generateAdapter = true)
data class StatusRes(
    val name: String = "",
    val firmware: String = "",
    val uptime_ms: Long = 0,
    val armed: Boolean = false,
    val status: String = "",
    val ip: String = "",
)
