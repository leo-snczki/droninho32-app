package pt.droninho32.app.data.repo

import pt.droninho32.app.data.api.NetworkModule
import pt.droninho32.app.data.dto.Command
import pt.droninho32.app.data.dto.CommandRes
import pt.droninho32.app.data.dto.StatusRes
import pt.droninho32.app.data.dto.TelemetryPoint
import pt.droninho32.app.domain.Outcome

/**
 * Comunicação direta com o drone (http://192.168.4.1). Usado no ecrã de controlo
 * para polling de telemetria, envio de comandos e heartbeat.
 */
class DroneRepository(
    private val network: NetworkModule,
) {
    private val api get() = network.droneApi

    suspend fun getStatus(): Outcome<StatusRes> = wrap { api.getStatus() }

    suspend fun getTelemetry(): Outcome<TelemetryPoint> = wrap { api.getTelemetry() }

    suspend fun sendCommand(command: Command): Outcome<CommandRes> = wrap { api.postCommand(command) }

    private inline fun <T> wrap(block: () -> T): Outcome<T> = try {
        Outcome.Ok(block())
    } catch (t: Throwable) {
        Outcome.Err(t.toUserMessage(), t)
    }
}
