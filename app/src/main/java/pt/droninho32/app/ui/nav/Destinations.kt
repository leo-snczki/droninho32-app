package pt.droninho32.app.ui.nav

/** Rotas de navegação (navigation-compose). Strings centralizadas para evitar erros. */
object Routes {
    const val LOGIN = "login"
    const val DRONES = "drones"
    const val CONTROL = "control"
    const val MAP = "map"
    const val HISTORY = "history"

    // Detalhe do voo recebe um id como argumento.
    const val FLIGHT_DETAIL = "flight/{flightId}"
    fun flightDetail(flightId: Int) = "flight/$flightId"

    const val ARG_FLIGHT_ID = "flightId"
}
