package pt.droninho32.app.data.repo

import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Extrai o campo "detail" do corpo de erro de uma resposta (as vistas de auth do
 * backend devolvem mensagens amigáveis em PT). Cai para [default] se não houver.
 */
internal fun Response<*>.serverDetail(default: String): String {
    val raw = try {
        errorBody()?.string()
    } catch (_: Exception) {
        null
    }
    if (raw.isNullOrBlank()) return default
    return try {
        JSONObject(raw).optString("detail").ifBlank { default }
    } catch (_: Exception) {
        default
    }
}

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
