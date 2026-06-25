package pt.droninho32.app.data.repo

import retrofit2.HttpException
import java.io.IOException

/** Converte exceções de rede/HTTP numa mensagem curta e legível (PT) para a UI. */
internal fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> when (code()) {
        400 -> "Pedido inválido (400)."
        401 -> "Sessão inválida ou expirada (401)."
        403 -> "Sem permissão (403)."
        404 -> "Recurso não encontrado (404)."
        in 500..599 -> "Erro no servidor (${code()})."
        else -> "Erro HTTP ${code()}."
    }
    is IOException -> "Sem ligação. Verifica a rede."
    else -> message ?: "Erro inesperado."
}
