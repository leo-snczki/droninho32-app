package pt.droninho32.app.data.api

import pt.droninho32.app.data.dto.Command
import pt.droninho32.app.data.dto.CommandRes
import pt.droninho32.app.data.dto.StatusRes
import pt.droninho32.app.data.dto.TelemetryPoint
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * API exposta pelo firmware do drone em http://192.168.4.1/ (ARCHITECTURE.md §3).
 * Sem autenticação; CORS aberto. A app fala diretamente quando ligada ao AP "Droninho32".
 */
interface DroneApi {

    @GET("api/status")
    suspend fun getStatus(): StatusRes

    @GET("api/telemetry")
    suspend fun getTelemetry(): TelemetryPoint

    @POST("api/command")
    suspend fun postCommand(@Body command: Command): CommandRes
}
