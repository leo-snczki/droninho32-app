package pt.droninho32.app.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import pt.droninho32.app.data.api.NetworkModule
import pt.droninho32.app.data.dto.LoginReq
import pt.droninho32.app.data.dto.RegisterReq
import pt.droninho32.app.data.dto.User
import pt.droninho32.app.data.store.SettingsStore
import pt.droninho32.app.domain.Outcome

/**
 * Gestão de sessão com o backend: registo, login, logout, perfil.
 *
 * Mantém um cache em memória do token ([cachedToken]) para o [pt.droninho32.app.data.api.AuthInterceptor]
 * o poder ler de forma síncrona, e persiste a sessão no [SettingsStore].
 */
class AuthRepository(
    private val network: NetworkModule,
    private val settings: SettingsStore,
) {
    /** Cache síncrono do token, lido pelo interceptor. */
    @Volatile
    var cachedToken: String? = null
        private set

    val tokenFlow: Flow<String?> = settings.token
    val usernameFlow: Flow<String?> = settings.username
    val backendUrlFlow: Flow<String> = settings.backendUrl

    /** Carrega o token persistido para o cache em memória (chamar no arranque). */
    suspend fun warmUp() {
        cachedToken = settings.token.first()
    }

    suspend fun currentBackendUrl(): String = settings.backendUrl.first()

    suspend fun setBackendUrl(url: String) = settings.setBackendUrl(url)

    suspend fun isLoggedIn(): Boolean = !settings.token.first().isNullOrBlank()

    suspend fun login(username: String, password: String): Outcome<User> = runCatching {
        val api = network.backendApi(currentBackendUrl())
        val res = api.login(LoginReq(username.trim(), password))
        cachedToken = res.token
        settings.saveSession(res.token, res.user.username)
        res.user
    }.fold(
        onSuccess = { Outcome.Ok(it) },
        onFailure = { Outcome.Err(it.toUserMessage(), it) },
    )

    suspend fun register(username: String, email: String, password: String): Outcome<User> =
        runCatching {
            val api = network.backendApi(currentBackendUrl())
            api.register(RegisterReq(username.trim(), email.trim(), password))
        }.fold(
            onSuccess = { Outcome.Ok(it) },
            onFailure = { Outcome.Err(it.toUserMessage(), it) },
        )

    suspend fun logout() {
        runCatching {
            val api = network.backendApi(currentBackendUrl())
            api.logout()
        }
        cachedToken = null
        settings.clearSession()
    }

    suspend fun me(): Outcome<User> = runCatching {
        network.backendApi(currentBackendUrl()).me()
    }.fold(
        onSuccess = { Outcome.Ok(it) },
        onFailure = { Outcome.Err(it.toUserMessage(), it) },
    )
}
