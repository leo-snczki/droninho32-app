package pt.droninho32.app.data.api

import pt.droninho32.app.data.dto.Drone
import pt.droninho32.app.data.dto.DroneReq
import pt.droninho32.app.data.dto.Flight
import pt.droninho32.app.data.dto.FlightReq
import pt.droninho32.app.data.dto.GeoJson
import pt.droninho32.app.data.dto.LoginReq
import pt.droninho32.app.data.dto.LoginRes
import pt.droninho32.app.data.dto.RegisterReq
import pt.droninho32.app.data.dto.Route
import pt.droninho32.app.data.dto.Stats
import pt.droninho32.app.data.dto.TelemetryBatch
import pt.droninho32.app.data.dto.TelemetryPoint
import pt.droninho32.app.data.dto.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * API do backend Django + DRF, base /api/v1/ (ARCHITECTURE.md §4).
 * Autenticação por Token (header Authorization: Token <token>), injetado pelo
 * AuthInterceptor para todas as rotas exceto register/login.
 */
interface BackendApi {

    // --- Autenticação ---

    @POST("api/v1/auth/register/")
    suspend fun register(@Body body: RegisterReq): User

    @POST("api/v1/auth/login/")
    suspend fun login(@Body body: LoginReq): LoginRes

    @POST("api/v1/auth/logout/")
    suspend fun logout(): Response<Unit>

    @GET("api/v1/auth/me/")
    suspend fun me(): User

    // --- Drones ---

    @GET("api/v1/drones/")
    suspend fun listDrones(): List<Drone>

    @POST("api/v1/drones/")
    suspend fun createDrone(@Body body: DroneReq): Drone

    @GET("api/v1/drones/{id}/")
    suspend fun getDrone(@Path("id") id: Int): Drone

    @PUT("api/v1/drones/{id}/")
    suspend fun updateDrone(@Path("id") id: Int, @Body body: DroneReq): Drone

    @PATCH("api/v1/drones/{id}/")
    suspend fun patchDrone(@Path("id") id: Int, @Body body: DroneReq): Drone

    @DELETE("api/v1/drones/{id}/")
    suspend fun deleteDrone(@Path("id") id: Int): Response<Unit>

    // --- Voos ---

    @GET("api/v1/flights/")
    suspend fun listFlights(): List<Flight>

    @POST("api/v1/flights/")
    suspend fun createFlight(@Body body: FlightReq): Flight

    @GET("api/v1/flights/{id}/")
    suspend fun getFlight(@Path("id") id: Int): Flight

    @DELETE("api/v1/flights/{id}/")
    suspend fun deleteFlight(@Path("id") id: Int): Response<Unit>

    /** Upload em lote dos pontos de telemetria de um voo. */
    @POST("api/v1/flights/{id}/telemetry/")
    suspend fun uploadTelemetry(
        @Path("id") flightId: Int,
        @Body body: TelemetryBatch,
    ): Response<Unit>

    @GET("api/v1/flights/{id}/telemetry/")
    suspend fun listTelemetry(@Path("id") flightId: Int): List<TelemetryPoint>

    /** Rota do voo como GeoJSON LineString. */
    @GET("api/v1/flights/{id}/route/")
    suspend fun getFlightRoute(@Path("id") flightId: Int): GeoJson

    @GET("api/v1/flights/{id}/stats/")
    suspend fun getFlightStats(@Path("id") flightId: Int): Stats

    // --- Rotas guardadas ---

    @GET("api/v1/routes/")
    suspend fun listRoutes(): List<Route>

    @POST("api/v1/routes/")
    suspend fun createRoute(@Body body: Route): Route

    @GET("api/v1/routes/{id}/")
    suspend fun getRoute(@Path("id") id: Int): Route

    @PUT("api/v1/routes/{id}/")
    suspend fun updateRoute(@Path("id") id: Int, @Body body: Route): Route

    @DELETE("api/v1/routes/{id}/")
    suspend fun deleteRoute(@Path("id") id: Int): Response<Unit>
}
