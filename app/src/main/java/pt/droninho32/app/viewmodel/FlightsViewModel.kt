package pt.droninho32.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import pt.droninho32.app.data.dto.Flight
import pt.droninho32.app.data.dto.Stats
import pt.droninho32.app.data.repo.BackendRepository
import pt.droninho32.app.domain.Outcome

data class FlightsUiState(
    val flights: List<Flight> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class FlightDetailUiState(
    val flight: Flight? = null,
    val stats: Stats? = null,
    /** Pontos da rota (lat/lon) para desenhar no mapa. */
    val routePoints: List<GeoPoint> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/** ViewModel do histórico de voos e do detalhe de um voo (com rota + stats). */
class FlightsViewModel(
    private val backend: BackendRepository,
) : ViewModel() {

    private val _list = MutableStateFlow(FlightsUiState())
    val list: StateFlow<FlightsUiState> = _list.asStateFlow()

    private val _detail = MutableStateFlow(FlightDetailUiState())
    val detail: StateFlow<FlightDetailUiState> = _detail.asStateFlow()

    fun refreshList() {
        viewModelScope.launch {
            _list.update { it.copy(loading = true, error = null) }
            when (val r = backend.listFlights()) {
                is Outcome.Ok -> _list.update { it.copy(loading = false, flights = r.value) }
                is Outcome.Err -> _list.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun loadDetail(flightId: Int) {
        viewModelScope.launch {
            _detail.update { FlightDetailUiState(loading = true) }

            val flightRes = backend.getFlight(flightId)
            val statsRes = backend.getFlightStats(flightId)
            val routeRes = backend.getFlightRoute(flightId)

            val flight = flightRes.getOrNull()
            val stats = statsRes.getOrNull()
            // GeoJSON LineString -> lista de [lon, lat]; GeoPoint recebe (lat, lon).
            val points = routeRes.getOrNull()?.lineCoordinates()?.mapNotNull { pair ->
                if (pair.size >= 2) GeoPoint(pair[1], pair[0]) else null
            } ?: emptyList()

            val error = listOf(flightRes, statsRes, routeRes)
                .filterIsInstance<Outcome.Err>()
                .firstOrNull()
                ?.message
                ?.takeIf { flight == null }

            _detail.update {
                FlightDetailUiState(
                    flight = flight,
                    stats = stats,
                    routePoints = points,
                    loading = false,
                    error = error,
                )
            }
        }
    }

    fun deleteFlight(id: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val r = backend.deleteFlight(id)) {
                is Outcome.Ok -> {
                    refreshList()
                    onDone()
                }
                is Outcome.Err -> _list.update { it.copy(error = r.message) }
            }
        }
    }

    class Factory(private val backend: BackendRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FlightsViewModel(backend) as T
        }
    }
}
