package pt.droninho32.app

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.droninho32.app.data.dto.Command
import pt.droninho32.app.data.dto.GeoJson
import pt.droninho32.app.data.dto.TelemetryPoint

/**
 * Testes unitários (JVM) que validam o mapeamento 1:1 dos DTOs com o contrato
 * (ARCHITECTURE.md §2 e §4). Correm com `./gradlew test`.
 */
class TelemetryJsonTest {

    // Usamos só reflexão aqui para o teste não depender do codegen KSP.
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Test
    fun `parse do TelemetryPoint canonico`() {
        val json = """
            {
              "ts": 1718450000,
              "uptime_ms": 123456,
              "armed": true,
              "status": "flying",
              "throttle": 35,
              "motors": [10, 20, 30, 40],
              "attitude": { "roll": 1.5, "pitch": -2.0, "yaw": 90.0 },
              "imu": { "ax": 0.1, "ay": 0.2, "az": 9.8, "gx": 0.0, "gy": 0.0, "gz": 0.0 },
              "gps": { "fix": true, "lat": 38.7223, "lon": -9.1393, "alt_m": 50.0, "sats": 8, "speed_mps": 4.2, "course_deg": 270.0 },
              "battery": { "voltage": 7.8, "percent": 75 },
              "rssi": -60
            }
        """.trimIndent()

        val t = moshi.adapter(TelemetryPoint::class.java).fromJson(json)!!

        assertEquals(1718450000L, t.ts)
        assertEquals(123456L, t.uptimeMs)
        assertTrue(t.armed)
        assertEquals("flying", t.status)
        assertEquals(35, t.throttle)
        assertEquals(listOf(10, 20, 30, 40), t.motors)
        assertEquals(90.0, t.attitude.yaw, 0.0001)
        assertEquals(9.8, t.imu.az, 0.0001)
        assertTrue(t.gps.fix)
        assertEquals(38.7223, t.gps.lat, 0.0001)
        assertEquals(-9.1393, t.gps.lon, 0.0001)
        assertEquals(8, t.gps.sats)
        assertEquals(75, t.battery.percent)
        assertEquals(-60, t.rssi)
    }

    @Test
    fun `valores em falta caem para defaults neutros`() {
        val t = moshi.adapter(TelemetryPoint::class.java).fromJson("{}")!!
        assertFalse(t.armed)
        assertEquals("idle", t.status)
        assertEquals(listOf(0, 0, 0, 0), t.motors)
        assertFalse(t.gps.fix)
    }

    @Test
    fun `comandos respeitam os limites`() {
        assertEquals(100, Command.throttle(150).value)
        assertEquals(0, Command.throttle(-5).value)
        val rc = Command.rc(roll = 200, pitch = -200, yaw = 0, throttle = 50)
        assertEquals(100, rc.roll)
        assertEquals(-100, rc.pitch)
        assertEquals("rc", rc.cmd)
    }

    @Test
    fun `geojson LineString extrai coordenadas`() {
        val json = """
            { "type": "LineString", "coordinates": [[-9.1, 38.7], [-9.2, 38.8]] }
        """.trimIndent()
        val geo = moshi.adapter(GeoJson::class.java).fromJson(json)!!
        val coords = geo.lineCoordinates()
        assertEquals(2, coords.size)
        assertEquals(-9.1, coords[0][0], 0.0001) // lon
        assertEquals(38.7, coords[0][1], 0.0001) // lat
    }
}
