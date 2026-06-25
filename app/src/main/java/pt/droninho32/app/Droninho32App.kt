package pt.droninho32.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import pt.droninho32.app.di.ServiceLocator

/**
 * Application: ponto de inicialização global.
 *  - cria o [ServiceLocator] (DI manual),
 *  - aquece o cache do token de autenticação,
 *  - configura o osmdroid (user-agent obrigatório pela política de uso do OSM).
 */
class Droninho32App : Application() {

    lateinit var locator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        locator = ServiceLocator(this)

        // Carrega o token persistido para o cache em memória usado pelo interceptor.
        CoroutineScope(Dispatchers.IO).launch {
            locator.authRepository.warmUp()
        }

        // osmdroid: definir user-agent (senão os servidores de tiles do OSM bloqueiam).
        Configuration.getInstance().userAgentValue = packageName
    }

    companion object {
        fun from(app: Application): Droninho32App = app as Droninho32App
    }
}
