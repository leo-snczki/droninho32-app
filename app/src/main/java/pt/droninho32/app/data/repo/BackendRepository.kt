package pt.droninho32.app.data.repo

import kotlinx.coroutines.flow.first
import pt.droninho32.app.data.api.NetworkModule
import pt.droninho32.app.data.dto.Drone
import pt.droninho32.app.data.dto.DroneReq
import pt.droninho32.app.data.dto.Flight
import pt.droninho32.app.data.dto.FlightReq
import pt.droninho32.app.data.dto.GeoJson
import pt.droninho32.app.data.dto.Stats
import pt.droninho32.app.data.dto.TelemetryBatch
import pt.droninho32.app.data.dto.TelemetryPoint
import pt.droninho32.app.data.store.SettingsStore
import pt.droninho32.app.domain.Outcome

/**
 * Acesso aos recursos do backend (drones, voos, telemetria, rotas, stats).
 * Recria o [pt.droninho32.app.data.api.BackendApi] a partir do URL atual em cada chamada,
 * para refletir alterações de configuração do utilizador sem reiniciar a app.
 */
class BackendRepository(
    private val network: NetworkModule,
    private val settings: SettingsStore,
) {
    private suspend fun api() = network.backendApi(settings.backendUrl.first())

    private inline fun <T> wrap(block: () -> T): Outcome<T> = try {
        Outcome.Ok(block())
    } catch (t: Throwable) {
        Outcome.Err(t.toUserMessage(), t)
    }

    // --- Drones ---

    suspend fun listDrones(): Outcome<List<Drone>> = wrap { api().listDrones() }

    suspend fun createDrone(req: DroneReq): Outcome<Drone> = wrap { api().createDrone(req) }

    suspend fun deleteDrone(id: Int): Outcome<Unit> = wrap { api().deleteDrone(id); Unit }

    // --- Voos ---

    suspend fun listFlights(): Outcome<List<Flight>> = wrap { api().listFlights() }

    suspend fun getFlight(id: Int): Outcome<Flight> = wrap { api().getFlight(id) }

    suspend fun getFlightStats(id: Int): Outcome<Stats> = wrap { api().getFlightStats(id) }

    suspend fun getFlightRoute(id: Int): Outcome<GeoJson> = wrap { api().getFlightRoute(id) }

    suspend fun deleteFlight(id: Int): Outcome<Unit> = wrap { api().deleteFlight(id); Unit }

    /**
     * Cria um voo e faz upload dos pontos em lote(s). Devolve o [Flight] criado.
     * Os pontos são enviados em blocos para não exceder limites de payload.
     */
    suspend fun createFlightWithTelemetry(
        req: FlightReq,
        points: List<TelemetryPoint>,
        chunkSize: Int = 500,
    ): Outcome<Flight> = wrap {
        val service = api()
        val flight = service.createFlight(req)
        if (points.isNotEmpty()) {
            points.chunked(chunkSize).forEach { chunk ->
                service.uploadTelemetry(flight.id, TelemetryBatch(chunk))
            }
        }
        flight
    }
}
