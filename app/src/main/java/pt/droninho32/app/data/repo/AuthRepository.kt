package pt.droninho32.app.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import pt.droninho32.app.data.api.NetworkModule
import pt.droninho32.app.data.dto.LoginReq
import pt.droninho32.app.data.dto.LoginVerifyReq
import pt.droninho32.app.data.dto.RegisterReq
import pt.droninho32.app.data.dto.ResendReq
import pt.droninho32.app.data.dto.User
import pt.droninho32.app.data.dto.VerifyEmailReq
import pt.droninho32.app.data.store.SettingsStore
import pt.droninho32.app.domain.Outcome

/**
 * Resultado do passo 1 do login (credenciais validadas).
 *
 *  - [OtpRequired]: falta o segundo fator; [method] é "email" ou "totp".
 *  - [EmailUnverified]: o e-mail tem de ser verificado antes de entrar.
 */
sealed interface LoginStep {
    data class OtpRequired(val method: String, val detail: String) : LoginStep
    data class EmailUnverified(val detail: String) : LoginStep
}

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

    /**
     * Passo 1 do login: valida credenciais e devolve o desafio de 2FA
     * ([LoginStep]). NÃO inicia sessão — só [loginVerify] o faz.
     */
    suspend fun login(username: String, password: String): Outcome<LoginStep> {
        return try {
            val api = network.backendApi(currentBackendUrl())
            val resp = api.login(LoginReq(username.trim(), password))
            when {
                resp.isSuccessful -> {
                    val b = resp.body()
                    Outcome.Ok(
                        LoginStep.OtpRequired(b?.method ?: "email", b?.detail ?: "")
                    )
                }
                resp.code() == 403 -> Outcome.Ok(
                    LoginStep.EmailUnverified(
                        resp.serverDetail(
                            "É obrigatório verificar o e-mail antes de entrar."
                        )
                    )
                )
                else -> Outcome.Err(resp.serverDetail("Credenciais inválidas."))
            }
        } catch (t: Throwable) {
            Outcome.Err(t.toUserMessage(), t)
        }
    }

    /**
     * Passo 2 do login: re-envia credenciais + código (TOTP, OTP por e-mail ou
     * código de backup). Em caso de sucesso guarda a sessão e devolve o [User].
     */
    suspend fun loginVerify(
        username: String,
        password: String,
        code: String,
    ): Outcome<User> {
        return try {
            val api = network.backendApi(currentBackendUrl())
            val resp = api.loginVerify(
                LoginVerifyReq(username.trim(), password, code.trim())
            )
            val body = resp.body()
            if (resp.isSuccessful && body != null) {
                cachedToken = body.token
                settings.saveSession(body.token, body.user.username)
                Outcome.Ok(body.user)
            } else {
                Outcome.Err(resp.serverDetail("Código inválido ou expirado."))
            }
        } catch (t: Throwable) {
            Outcome.Err(t.toUserMessage(), t)
        }
    }

    /** Confirma o código de verificação de e-mail recebido no registo. */
    suspend fun verifyEmail(username: String, code: String): Outcome<String> {
        return try {
            val api = network.backendApi(currentBackendUrl())
            val resp = api.verifyEmail(VerifyEmailReq(username.trim(), code.trim()))
            if (resp.isSuccessful) {
                Outcome.Ok(resp.body()?.detail ?: "E-mail verificado.")
            } else {
                Outcome.Err(resp.serverDetail("Código inválido ou expirado."))
            }
        } catch (t: Throwable) {
            Outcome.Err(t.toUserMessage(), t)
        }
    }

    /** Reenvia o código de verificação de e-mail (resposta sempre genérica). */
    suspend fun resendVerification(username: String): Outcome<String> {
        return try {
            val api = network.backendApi(currentBackendUrl())
            val resp = api.resendVerification(ResendReq(username.trim()))
            Outcome.Ok(
                resp.body()?.detail
                    ?: "Se a conta existir e estiver por verificar, enviámos um "
                    + "novo código."
            )
        } catch (t: Throwable) {
            Outcome.Err(t.toUserMessage(), t)
        }
    }

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
