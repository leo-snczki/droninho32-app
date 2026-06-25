package pt.droninho32.app.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import pt.droninho32.app.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Fábrica central de clientes de rede. Mantém:
 *  - um Moshi partilhado (adaptadores gerados por codegen + reflexão de fallback),
 *  - o [DroneApi] (baseUrl fixo do drone, timeouts curtos para deteção rápida de queda),
 *  - o [BackendApi] (baseUrl configurável; recriado quando o URL muda).
 *
 * O token é fornecido por [tokenProvider] (cache em memória do repositório de auth).
 */
class NetworkModule(
    private val tokenProvider: () -> String?,
) {

    val moshi: Moshi = Moshi.Builder()
        // Codegen trata os @JsonClass(generateAdapter = true); o factory de reflexão
        // cobre quaisquer classes Kotlin sem adaptador gerado (robustez).
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    // Cliente para o drone: timeouts curtos (rede local; queremos detetar perda de AP depressa).
    private val droneClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    // Cliente para o backend: timeouts mais folgados + injeção de token.
    private val backendClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(tokenProvider))
        .addInterceptor(loggingInterceptor)
        .build()

    /** Cliente do drone (baseUrl fixo 192.168.4.1). Singleton. */
    val droneApi: DroneApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.DRONE_URL)
            .client(droneClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DroneApi::class.java)
    }

    /**
     * Cria um [BackendApi] para o [baseUrl] dado. Como o URL é configurável em runtime,
     * o repositório pede uma nova instância sempre que o URL muda.
     */
    fun backendApi(baseUrl: String): BackendApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(backendClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BackendApi::class.java)
    }
}
