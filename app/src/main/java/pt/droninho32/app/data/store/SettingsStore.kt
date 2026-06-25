package pt.droninho32.app.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.droninho32.app.BuildConfig

private val Context.dataStore by preferencesDataStore(name = "droninho32_settings")

/** Offsets de centro (neutro) dos dois joysticks, em unidades normalizadas [-1, 1]. */
data class JoystickCalibration(
    val leftX: Float = 0f,
    val leftY: Float = 0f,
    val rightX: Float = 0f,
    val rightY: Float = 0f,
)

/**
 * Persistência simples (DataStore Preferences) do token de autenticação, do utilizador
 * atual, do URL base do backend (editável) e da calibração dos joysticks.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val TOKEN = stringPreferencesKey("auth_token")
        val USERNAME = stringPreferencesKey("username")
        val BACKEND_URL = stringPreferencesKey("backend_url")
        val CAL_LX = floatPreferencesKey("cal_left_x")
        val CAL_LY = floatPreferencesKey("cal_left_y")
        val CAL_RX = floatPreferencesKey("cal_right_x")
        val CAL_RY = floatPreferencesKey("cal_right_y")
    }

    val token: Flow<String?> = context.dataStore.data.map { it[Keys.TOKEN] }

    val username: Flow<String?> = context.dataStore.data.map { it[Keys.USERNAME] }

    /** URL base do backend; cai para o default do BuildConfig se não definido. */
    val backendUrl: Flow<String> = context.dataStore.data.map {
        it[Keys.BACKEND_URL]?.takeIf(String::isNotBlank) ?: BuildConfig.DEFAULT_BACKEND_URL
    }

    /** Calibração atual dos joysticks (offsets de centro). */
    val calibration: Flow<JoystickCalibration> = context.dataStore.data.map {
        JoystickCalibration(
            leftX = it[Keys.CAL_LX] ?: 0f,
            leftY = it[Keys.CAL_LY] ?: 0f,
            rightX = it[Keys.CAL_RX] ?: 0f,
            rightY = it[Keys.CAL_RY] ?: 0f,
        )
    }

    suspend fun saveSession(token: String, username: String) {
        context.dataStore.edit {
            it[Keys.TOKEN] = token
            it[Keys.USERNAME] = username
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(Keys.TOKEN)
            it.remove(Keys.USERNAME)
        }
    }

    suspend fun setBackendUrl(url: String) {
        context.dataStore.edit {
            // Garante barra final para o Retrofit.
            it[Keys.BACKEND_URL] = if (url.endsWith("/")) url else "$url/"
        }
    }

    suspend fun setCalibration(c: JoystickCalibration) {
        context.dataStore.edit {
            it[Keys.CAL_LX] = c.leftX
            it[Keys.CAL_LY] = c.leftY
            it[Keys.CAL_RX] = c.rightX
            it[Keys.CAL_RY] = c.rightY
        }
    }

    suspend fun resetCalibration() {
        context.dataStore.edit {
            it.remove(Keys.CAL_LX)
            it.remove(Keys.CAL_LY)
            it.remove(Keys.CAL_RX)
            it.remove(Keys.CAL_RY)
        }
    }
}
