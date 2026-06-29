package pt.droninho32.app.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ------------------------------------------------------------------
// DTOs do backend Django + DRF (secção 4 do ARCHITECTURE.md).
// Datas em ISO-8601 UTC (strings). Coordenadas em GeoJSON (WGS84).
// ------------------------------------------------------------------

// --- Autenticação ---

@JsonClass(generateAdapter = true)
data class RegisterReq(
    val username: String,
    val email: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class LoginReq(
    val username: String,
    val password: String,
)

/**
 * Login passo 1 (200): o servidor NÃO devolve token; indica o método de 2FA a
 * usar (e-mail ou app autenticadora). Se o e-mail não estiver verificado, o
 * servidor responde 403 (ver tratamento no AuthRepository).
 */
@JsonClass(generateAdapter = true)
data class LoginStep1Res(
    @Json(name = "otp_required") val otpRequired: Boolean = false,
    val method: String = "email",
    val detail: String = "",
)

/** Login passo 2: credenciais + código (TOTP, OTP por e-mail ou código de backup). */
@JsonClass(generateAdapter = true)
data class LoginVerifyReq(
    val username: String,
    val password: String,
    val code: String,
)

@JsonClass(generateAdapter = true)
data class LoginRes(
    val token: String,
    val user: User,
)

/** Confirmação do código de verificação de e-mail. */
@JsonClass(generateAdapter = true)
data class VerifyEmailReq(
    val username: String,
    val code: String,
)

/** Reenvio do código de verificação de e-mail. */
@JsonClass(generateAdapter = true)
data class ResendReq(
    val username: String,
)

/** Resposta simples {detail, email_verified} de vários endpoints de auth. */
@JsonClass(generateAdapter = true)
data class SimpleRes(
    val detail: String = "",
    @Json(name = "email_verified") val emailVerified: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class User(
    val id: Int,
    val username: String,
    val email: String = "",
)

// --- Drones ---

@JsonClass(generateAdapter = true)
data class Drone(
    val id: Int = 0,
    val name: String,
    val model: String = "",
    @Json(name = "firmware_version") val firmwareVersion: String = "",
    @Json(name = "created_at") val createdAt: String? = null,
)

/** Corpo para registar/editar um drone (sem id/created_at, geridos pelo servidor). */
@JsonClass(generateAdapter = true)
data class DroneReq(
    val name: String,
    val model: String = "",
    @Json(name = "firmware_version") val firmwareVersion: String = "",
)

// --- Voos ---

@JsonClass(generateAdapter = true)
data class Flight(
    val id: Int = 0,
    val drone: Int? = null,
    @Json(name = "drone_name") val droneName: String? = null,
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "ended_at") val endedAt: String? = null,
    val status: String = "",
    val notes: String = "",
    @Json(name = "distance_m") val distanceM: Double = 0.0,
    @Json(name = "duration_s") val durationS: Double = 0.0,
    @Json(name = "max_alt_m") val maxAltM: Double = 0.0,
    @Json(name = "max_speed_mps") val maxSpeedMps: Double = 0.0,
    @Json(name = "points") val points: Int = 0,
)

/** Corpo para criar um voo. O backend devolve um [Flight] com id. */
@JsonClass(generateAdapter = true)
data class FlightReq(
    val drone: Int? = null,
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "ended_at") val endedAt: String? = null,
    val status: String = "completed",
    val notes: String = "",
)

/** Corpo de POST /flights/{id}/telemetry/ — upload em lote. */
@JsonClass(generateAdapter = true)
data class TelemetryBatch(
    val points: List<TelemetryPoint>,
)

@JsonClass(generateAdapter = true)
data class Stats(
    @Json(name = "distance_m") val distanceM: Double = 0.0,
    @Json(name = "duration_s") val durationS: Double = 0.0,
    @Json(name = "max_alt_m") val maxAltM: Double = 0.0,
    @Json(name = "max_speed_mps") val maxSpeedMps: Double = 0.0,
    @Json(name = "avg_speed_mps") val avgSpeedMps: Double = 0.0,
    val points: Int = 0,
)

// --- GeoJSON (rota do voo / rotas guardadas) ---

/**
 * GeoJSON genérico para uma LineString (rota). O backend devolve algo como:
 * { "type": "LineString", "coordinates": [[lon,lat],[lon,lat],...] }
 * ou um Feature que embrulha essa geometria. Suportamos ambos com [geometryOrNull].
 */
@JsonClass(generateAdapter = true)
data class GeoJson(
    val type: String = "",
    /** Presente quando type == "LineString". Cada par é [lon, lat]. */
    val coordinates: List<List<Double>>? = null,
    /** Presente quando type == "Feature". */
    val geometry: GeoGeometry? = null,
) {
    /** Devolve a lista de coordenadas [lon,lat] independentemente do envelope. */
    fun lineCoordinates(): List<List<Double>> =
        when {
            coordinates != null -> coordinates
            geometry?.coordinates != null -> geometry.coordinates
            else -> emptyList()
        }
}

@JsonClass(generateAdapter = true)
data class GeoGeometry(
    val type: String = "",
    val coordinates: List<List<Double>>? = null,
)

@JsonClass(generateAdapter = true)
data class Route(
    val id: Int = 0,
    val name: String,
    val path: GeoJson? = null,
    @Json(name = "source_flight") val sourceFlight: Int? = null,
    @Json(name = "created_at") val createdAt: String? = null,
)
