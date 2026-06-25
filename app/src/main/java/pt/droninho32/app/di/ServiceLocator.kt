package pt.droninho32.app.di

import android.content.Context
import pt.droninho32.app.data.api.NetworkModule
import pt.droninho32.app.data.repo.AuthRepository
import pt.droninho32.app.data.repo.BackendRepository
import pt.droninho32.app.data.repo.DroneRepository
import pt.droninho32.app.data.store.SettingsStore

/**
 * Contentor de dependências manual (sem Hilt/Koin para manter o MVP leve e legível).
 * Inicializado uma vez em [pt.droninho32.app.Droninho32App.onCreate]. Os ViewModels
 * obtêm os repositórios através das suas Factories.
 */
class ServiceLocator(appContext: Context) {

    val settings: SettingsStore = SettingsStore(appContext)

    // A rede precisa de ler o token de forma síncrona -> usa o cache do AuthRepository.
    // Resolvemos a ordem de inicialização com um provider lateinit-safe.
    private val network: NetworkModule = NetworkModule(
        tokenProvider = { authRepository.cachedToken },
    )

    val authRepository: AuthRepository = AuthRepository(network, settings)
    val backendRepository: BackendRepository = BackendRepository(network, settings)
    val droneRepository: DroneRepository = DroneRepository(network)
}
