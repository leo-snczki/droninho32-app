package pt.droninho32.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.droninho32.app.data.repo.AuthRepository
import pt.droninho32.app.domain.Outcome

data class AuthUiState(
    val loggedIn: Boolean = false,
    val username: String? = null,
    val backendUrl: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    /** Mensagem de sucesso pontual (ex.: conta criada). */
    val info: String? = null,
    /** true depois de um registo bem-sucedido, para a UI voltar ao modo login. */
    val justRegistered: Boolean = false,
)

/** ViewModel da autenticação: login, registo, logout e configuração do URL do backend. */
class AuthViewModel(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val url = auth.currentBackendUrl()
            val loggedIn = auth.isLoggedIn()
            val name = auth.usernameFlow.first()
            _state.update { it.copy(loggedIn = loggedIn, username = name, backendUrl = url) }
        }
    }

    fun onBackendUrlChange(url: String) {
        _state.update { it.copy(backendUrl = url) }
    }

    private fun persistBackendUrl() {
        viewModelScope.launch { auth.setBackendUrl(_state.value.backendUrl) }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Preenche utilizador e palavra-passe.") }
            return
        }
        persistBackendUrl()
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, info = null) }
            when (val r = auth.login(username, password)) {
                is Outcome.Ok -> _state.update {
                    it.copy(loading = false, loggedIn = true, username = r.value.username)
                }
                is Outcome.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun register(username: String, email: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Preenche utilizador e palavra-passe.") }
            return
        }
        persistBackendUrl()
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, info = null) }
            when (val r = auth.register(username, email, password)) {
                is Outcome.Ok -> _state.update {
                    it.copy(
                        loading = false,
                        justRegistered = true,
                        info = "Conta criada. Já podes entrar.",
                    )
                }
                is Outcome.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun consumeJustRegistered() {
        _state.update { it.copy(justRegistered = false) }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, info = null) }
    }

    fun logout() {
        viewModelScope.launch {
            auth.logout()
            _state.update { it.copy(loggedIn = false, username = null) }
        }
    }

    class Factory(private val auth: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(auth) as T
        }
    }
}
