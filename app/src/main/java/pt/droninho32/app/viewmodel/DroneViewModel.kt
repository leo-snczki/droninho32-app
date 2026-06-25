package pt.droninho32.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.droninho32.app.data.dto.Drone
import pt.droninho32.app.data.dto.DroneReq
import pt.droninho32.app.data.repo.BackendRepository
import pt.droninho32.app.domain.Outcome

data class DroneUiState(
    val drones: List<Drone> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/** ViewModel da lista/registo de drones (recurso do backend). */
class DroneViewModel(
    private val backend: BackendRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DroneUiState())
    val state: StateFlow<DroneUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = backend.listDrones()) {
                is Outcome.Ok -> _state.update { it.copy(loading = false, drones = r.value) }
                is Outcome.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun addDrone(name: String, model: String, firmware: String, onDone: () -> Unit = {}) {
        if (name.isBlank()) {
            _state.update { it.copy(error = "O nome do drone é obrigatório.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = backend.createDrone(DroneReq(name.trim(), model.trim(), firmware.trim()))) {
                is Outcome.Ok -> {
                    refresh()
                    onDone()
                }
                is Outcome.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun deleteDrone(id: Int) {
        viewModelScope.launch {
            when (val r = backend.deleteDrone(id)) {
                is Outcome.Ok -> refresh()
                is Outcome.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    class Factory(private val backend: BackendRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DroneViewModel(backend) as T
        }
    }
}
