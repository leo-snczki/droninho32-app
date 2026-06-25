package pt.droninho32.app.domain

/**
 * Resultado simples de uma operação que pode falhar, usado pelos repositórios.
 * Evitamos lançar exceções até à camada de UI; os ViewModels mapeiam para estado.
 */
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val message: String, val cause: Throwable? = null) : Outcome<Nothing>

    val isOk: Boolean get() = this is Ok

    fun getOrNull(): T? = (this as? Ok)?.value
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}
