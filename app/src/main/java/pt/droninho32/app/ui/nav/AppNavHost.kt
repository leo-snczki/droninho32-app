package pt.droninho32.app.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pt.droninho32.app.di.ServiceLocator
import pt.droninho32.app.ui.screens.ControlScreen
import pt.droninho32.app.ui.screens.DroneListScreen
import pt.droninho32.app.ui.screens.FlightDetailScreen
import pt.droninho32.app.ui.screens.HistoryScreen
import pt.droninho32.app.ui.screens.LoginScreen
import pt.droninho32.app.ui.screens.MapScreen
import pt.droninho32.app.viewmodel.AuthViewModel
import pt.droninho32.app.viewmodel.ControlViewModel
import pt.droninho32.app.viewmodel.DroneViewModel
import pt.droninho32.app.viewmodel.FlightsViewModel

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem(Routes.CONTROL, "Controlo", Icons.Default.FlightTakeoff),
    BottomItem(Routes.HISTORY, "Histórico", Icons.Default.History),
    BottomItem(Routes.DRONES, "Drones", Icons.Default.Memory),
)

/**
 * Grafo de navegação da app. O ControlViewModel é criado ao nível da Activity
 * (owner partilhado) para que ControlScreen e MapScreen vejam a MESMA telemetria.
 */
@Composable
fun AppNavHost(
    locator: ServiceLocator,
    controlVm: ControlViewModel,
    activityViewModelOwner: androidx.lifecycle.ViewModelStoreOwner,
    startLoggedIn: Boolean,
    onToggleScreenRecording: () -> Unit,
) {
    val nav = rememberNavController()

    // ViewModel de autenticação partilhado ao nível da Activity.
    // (o ControlViewModel é criado na MainActivity e passado aqui)
    val authVm: AuthViewModel = viewModel(
        viewModelStoreOwner = activityViewModelOwner,
        factory = AuthViewModel.Factory(locator.authRepository),
    )

    val start = if (startLoggedIn) Routes.CONTROL else Routes.LOGIN

    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    nav.navigate(item.route) {
                                        popUpTo(Routes.CONTROL) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            authGraph(nav, authVm)
            mainGraph(nav, locator, controlVm, authVm, onToggleScreenRecording)
        }
    }
}

private fun NavGraphBuilder.authGraph(nav: NavHostController, authVm: AuthViewModel) {
    composable(Routes.LOGIN) {
        LoginScreen(
            vm = authVm,
            onLoggedIn = {
                nav.navigate(Routes.CONTROL) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            },
            onSkip = {
                nav.navigate(Routes.CONTROL) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            },
        )
    }
}

private fun NavGraphBuilder.mainGraph(
    nav: NavHostController,
    locator: ServiceLocator,
    controlVm: ControlViewModel,
    authVm: AuthViewModel,
    onToggleScreenRecording: () -> Unit,
) {
    composable(Routes.CONTROL) {
        ControlScreen(
            vm = controlVm,
            onOpenMap = { nav.navigate(Routes.MAP) },
            onToggleScreenRecording = onToggleScreenRecording,
        )
    }

    composable(Routes.MAP) {
        MapScreen(
            vm = controlVm,
            onBack = { nav.popBackStack() },
        )
    }

    composable(Routes.DRONES) {
        val droneVm: DroneViewModel = viewModel(
            factory = DroneViewModel.Factory(locator.backendRepository),
        )
        DroneListScreen(
            vm = droneVm,
            onOpenControl = { drone ->
                controlVm.selectDrone(drone.id)
                nav.navigate(Routes.CONTROL) { launchSingleTop = true }
            },
            onLogout = {
                // Limpa a sessão e volta ao login, esvaziando a pilha de navegação.
                authVm.logout()
                nav.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            },
        )
    }

    composable(Routes.HISTORY) {
        val flightsVm: FlightsViewModel = viewModel(
            factory = FlightsViewModel.Factory(locator.backendRepository),
        )
        HistoryScreen(
            vm = flightsVm,
            onOpenFlight = { id -> nav.navigate(Routes.flightDetail(id)) },
        )
    }

    composable(
        route = Routes.FLIGHT_DETAIL,
        arguments = listOf(navArgument(Routes.ARG_FLIGHT_ID) { type = NavType.IntType }),
    ) { entry ->
        val id = entry.arguments?.getInt(Routes.ARG_FLIGHT_ID) ?: 0
        val flightsVm: FlightsViewModel = viewModel(
            factory = FlightsViewModel.Factory(locator.backendRepository),
        )
        FlightDetailScreen(
            flightId = id,
            vm = flightsVm,
            onBack = { nav.popBackStack() },
        )
    }
}
