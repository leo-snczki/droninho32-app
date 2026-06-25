package pt.droninho32.app.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Injeta o header Authorization: Token <token> em todos os pedidos ao backend,
 * exceto nas rotas de autenticação iniciais (register/login) que ainda não têm token.
 *
 * O token é lido de forma síncrona através de [tokenProvider], alimentado por um
 * cache em memória que o repositório mantém atualizado a partir do DataStore.
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        val skip = path.endsWith("/auth/login/") || path.endsWith("/auth/register/")
        val token = tokenProvider()

        if (skip || token.isNullOrBlank()) {
            return chain.proceed(request)
        }

        val authed = request.newBuilder()
            .header("Authorization", "Token $token")
            .build()
        return chain.proceed(authed)
    }
}
