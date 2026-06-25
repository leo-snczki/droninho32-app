package pt.droninho32.app.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Esquema canónico de telemetria — mapeia 1:1 o JSON da secção 2 do ARCHITECTURE.md.
 *
 * O firmware produz este objeto, a app consome-o ao vivo e reenvia-o em lote para o
 * backend. Todos os campos estão sempre presentes; usamos valores neutros por omissão
 * para tolerar firmware antigo, mas o contrato garante presença.
 */
@JsonClass(generateAdapter = true)
data class TelemetryPoint(
    val ts: Long = 0,
    @Json(name = "uptime_ms") val uptimeMs: Long = 0,
    val armed: Boolean = false,
    val status: String = "idle",
    val throttle: Int = 0,
    /** Potência efetiva por motor, ordem FL, FR, RL, RR (0..100). */
    val motors: List<Int> = listOf(0, 0, 0, 0),
    val attitude: Attitude = Attitude(),
    val imu: Imu = Imu(),
    val gps: Gps = Gps(),
    val battery: Battery = Battery(),
    /** Força do sinal WiFi em dBm. */
    val rssi: Int = 0,
) {
    /** Atalho: há solução de GPS válida? */
    val hasFix: Boolean get() = gps.fix
}

@JsonClass(generateAdapter = true)
data class Attitude(
    val roll: Double = 0.0,
    val pitch: Double = 0.0,
    val yaw: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class Imu(
    val ax: Double = 0.0,
    val ay: Double = 0.0,
    val az: Double = 0.0,
    val gx: Double = 0.0,
    val gy: Double = 0.0,
    val gz: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class Gps(
    val fix: Boolean = false,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    @Json(name = "alt_m") val altM: Double = 0.0,
    val sats: Int = 0,
    @Json(name = "speed_mps") val speedMps: Double = 0.0,
    @Json(name = "course_deg") val courseDeg: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class Battery(
    val voltage: Double = 0.0,
    val percent: Int = 0,
)
