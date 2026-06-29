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
import pt.droninho32.app.data.repo.LoginStep
import pt.droninho32.app.domain.Outcome

data class AuthUiState(
    val loggedIn: Boolean = false,
    val username: String? = null,
    val backendUrl: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    /** Mensagem de sucesso/informação pontual. */
    val info: String? = null,
    /** Passo 2 do login: à espera do código (segundo fator). */
    val awaitingOtp: Boolean = false,
    /** Método de 2FA em curso: "email" ou "totp". */
    val otpMethod: String = "email",
    /** É obrigatório verificar o e-mail antes de entrar. */
    val needsEmailVerification: Boolean = false,
    /** Utilizador em fluxo de OTP/verificação (para reenvios e confirmação). */
    val pendingUsername: String? = null,
)

/**
 * ViewModel da autenticação multifator: login em 2 passos (credenciais → código),
 * verificação de e-mail obrigatória, registo, logout e URL do backend.
 *
 * A palavra-passe do passo 1 é mantida apenas em memória ([pendingPassword]) para
 * o passo 2 (o backend exige-a de novo); nunca é persistida.
 */
class AuthViewModel(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private var pendingPassword: String = ""

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

    // ----------------------------------------------------------------- //
    //  Passo 1: credenciais → desafio de 2FA
    // ----------------------------------------------------------------- //
    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Preenche utilizador e palavra-passe.") }
            return
        }
        persistBackendUrl()
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, info = null) }
            when (val r = auth.login(username, password)) {
                is Outcome.Ok -> when (val step = r.value) {
                    is LoginStep.OtpRequired -> {
                        pendingPassword = password
                        _state.update {
                            it.copy(
                                loading = false,
                                awaitingOtp = true,
                                otpMethod = step.method,
                                pendingUsername = username.trim(),
                                info = step.detail.ifBlank { null },
                            )
                        }
                    }
                    is LoginStep.EmailUnverified -> _state.update {
                        it.copy(
                            loading = false,
                            needsEmailVerification = true,
                            pendingUsername = username.trim(),
                            info = step.detail.ifBlank { null },
                        )
                    }
                }
                is Outcome.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    // ----------------------------------------------------------------- //
    //  Passo 2: código (TOTP / OTP por e-mail / código de backup) → sessão
    // ----------------------------------------------------------------- //
    fun submitOtp(code: String) {
        val user = _state.value.pendingUsername
        if (user.isNullOrBlank() || code.isBlank()) {
            _state.update { it.copy(error = "Introduz o código.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = auth.loginVerify(user, pendingPassword, code)) {
                is Outcome.Ok -> {
                    pendingPassword = ""
                    _state.update {
                        it.copy(
                            loading = false,
                            loggedIn = true,
                            username = r.value.username,
                            awaitingOtp = false,
                            pendingUsername = null,
                            info = null,
                            error = null,
                        )
                    }
                }
                is Outcome.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    /** Reenvia o OTP de login por e-mail (só faz sentido quando o método é e-mail). */
    fun resendLoginOtp() {
        val user = _state.value.pendingUsername ?: return
        viewModelScope.launch {
            when (val r = auth.login(user, pendingPassword)) {
                is Outcome.Ok -> _state.update {
                    it.copy(info = "Enviámos um novo código.", error = null)
                }
                is Outcome.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }

    // ----------------------------------------------------------------- //
    //  Verificação de e-mail (obrigatória)
    // ----------------------------------------------------------------- //
    fun submitEmailVerification(code: String) {
        val user = _state.value.pendingUsername
        if (user.isNullOrBlank() || code.isBlank()) {
            _state.update { it.copy(error = "Introduz o código de verificação.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = auth.verifyEmail(user, code)) {
                is Outcome.Ok -> _state.update {
                    it.copy(
                        loading = false,
                        needsEmailVerification = false,
                        info = "E-mail verificado. Já podes entrar.",
                        error = null,
                    )
                }
                is Outcome.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun resendVerification() {
        val user = _state.value.pendingUsername ?: return
        viewModelScope.launch {
            when (val r = auth.resendVerification(user)) {
                is Outcome.Ok -> _state.update { it.copy(info = r.value, error = null) }
                is Outcome.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }

    // ----------------------------------------------------------------- //
    //  Registo
    // ----------------------------------------------------------------- //
    fun register(username: String, email: String, password: String) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            _state.update {
                it.copy(error = "Preenche utilizador, e-mail e palavra-passe.")
            }
            return
        }
        persistBackendUrl()
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, info = null) }
            when (val r = auth.register(username, email, password)) {
                is Outcome.Ok -> _state.update {
                    // Conta criada mas por verificar → vai direto introduzir o código.
                    it.copy(
                        loading = false,
                        needsEmailVerification = true,
                        pendingUsername = username.trim(),
                        info = "Conta criada. Enviámos um código de verificação "
                            + "para o teu e-mail.",
                    )
                }
                is Outcome.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    /** Cancela o fluxo de OTP/verificação e volta ao formulário inicial. */
    fun cancelFlow() {
        pendingPassword = ""
        _state.update {
            it.copy(
                awaitingOtp = false,
                needsEmailVerification = false,
                pendingUsername = null,
                error = null,
                info = null,
            )
        }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, info = null) }
    }

    fun logout() {
        viewModelScope.launch {
            auth.logout()
            pendingPassword = ""
            _state.update {
                it.copy(
                    loggedIn = false,
                    username = null,
                    awaitingOtp = false,
                    needsEmailVerification = false,
                    pendingUsername = null,
                )
            }
        }
    }

    class Factory(private val auth: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(auth) as T
        }
    }
}
