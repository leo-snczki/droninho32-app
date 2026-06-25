# Droninho32 — App Android

App de controlo do drone **Droninho32**, em **Kotlin + Jetpack Compose** (Material 3),
arquitetura **MVVM**. Faz a ponte entre o drone (WiFi direto) e o backend Django:

- **Modo direto** (sem Internet): liga-te ao Access Point WiFi do drone (`Droninho32`)
  e controla-o em tempo real em `http://192.168.4.1` — armar/desarmar, acelerador,
  paragem de emergência e telemetria ao vivo.
- **Modo nuvem**: com Internet, faz login no backend, guarda os voos gravados
  (telemetria + rota), consulta o histórico e vê as rotas no mapa.

> Esta app respeita o contrato em [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md)
> (secções 2, 3, 4, 6 e 8). Os DTOs Kotlin mapeiam 1:1 os JSON do firmware e do backend.

---

## Requisitos

- **Android Studio** (Koala/2024.1 ou mais recente recomendado).
- **JDK 17** (incluído no Android Studio).
- **Android SDK** com `compileSdk 34`.
- Dispositivo/emulador com **Android 8.0 (API 26)** ou superior.

---

## Como abrir e compilar (Android Studio — recomendado)

1. Abre o Android Studio → **Open** → seleciona a pasta `droninho32-app`.
2. O Android Studio sincroniza o Gradle e **gera automaticamente o `gradle-wrapper.jar`**
   e o `local.properties` (com o caminho do SDK). Aceita as descargas de plugins/SDK.
3. Escolhe um dispositivo/emulador e carrega em **Run ▶**.

### Linha de comandos

> **Nota sobre o Gradle Wrapper:** este repositório **não inclui** o binário
> `gradle/wrapper/gradle-wrapper.jar` (não se versionam binários). Tens de o gerar
> uma vez. Se tiveres o Gradle 8.7 instalado:
>
> ```bash
> gradle wrapper --gradle-version 8.7
> ```
>
> A partir daí (ou simplesmente abrindo o projeto no Android Studio, que o gera):
>
> ```bash
> ./gradlew assembleDebug          # compila o APK de debug
> ./gradlew test                   # testes unitários (JVM)
> ./gradlew connectedAndroidCheck  # testes instrumentados (precisa de device/emulador)
> ```
>
> Em Windows usa `gradlew.bat` em vez de `./gradlew`.

O APK de debug fica em `app/build/outputs/apk/debug/`.

---

## Configuração

### URL do backend

O URL base do backend é **editável no ecrã de login** e fica guardado (DataStore).
Valor por omissão (definido em `app/build.gradle.kts` → `DEFAULT_BACKEND_URL`):

| Cenário | URL a usar |
|---------|-----------|
| **Emulador Android** a falar com Django no PC | `http://10.0.2.2:8000/` (default) |
| **Telemóvel real** na mesma rede que o PC | `http://<IP-do-PC>:8000/` |
| Backend publicado | `https://o-teu-dominio/` |

> `10.0.2.2` é o alias especial do emulador para o `localhost` da máquina anfitriã.

### Ligar ao drone (modo direto)

1. No telemóvel, abre as definições de **WiFi** e liga-te à rede do drone:
   - SSID: **`Droninho32`**
   - Palavra-passe: **`droninho32`**
2. Volta à app, no ecrã **Controlo**. O IP do drone (`http://192.168.4.1`) está fixo
   no `BuildConfig` (`DRONE_URL`), conforme o contrato.
3. **Segurança:** testa **sempre sem hélices** primeiro. Mantém distância. O botão
   vermelho de **paragem de emergência** corta os motores imediatamente.

> Em modo direto não há Internet, por isso o login no backend não funciona enquanto
> estiveres ligado ao AP do drone. Fluxo típico: liga-te ao AP, voa e grava; depois
> volta a uma rede com Internet e a app envia o voo gravado para o backend.

---

## Funcionalidades (MVP)

- **Login / Registo** no backend (token guardado em DataStore; injetado no header
  `Authorization: Token <token>`).
- **Lista de drones** (CRUD básico: listar, registar, apagar).
- **Controlo em tempo real:**
  - estado da ligação ao drone,
  - **ARMAR / DESARMAR** (armar exige acelerador a 0 — regra de segurança §7.3),
  - **slider de acelerador** (0–100 %),
  - **paragem de emergência** grande e destacada,
  - **telemetria ao vivo** (estado, motores, atitude, bateria, GPS, RSSI),
  - **heartbeat ~2 Hz** enquanto armado (satisfaz o failsafe do firmware),
  - **polling de telemetria ~3 Hz**.
- **Gravação de voo:** entre ARMAR e a paragem/DESARMAR, a app grava cada
  `TelemetryPoint`; ao terminar, **cria o voo no backend e faz upload em lote** da
  telemetria (se houver sessão; caso contrário fica local).
- **Mapa ao vivo** (osmdroid / OpenStreetMap): posição do drone + rasto da rota.
- **Histórico de voos** (do backend) e **detalhe do voo** (estatísticas + rota no mapa).

### Ecrãs

| Ecrã | Ficheiro | Função |
|------|----------|--------|
| Login | `ui/screens/LoginScreen.kt` | login/registo + URL do backend |
| Drones | `ui/screens/DroneListScreen.kt` | listar/registar/apagar drones |
| Controlo | `ui/screens/ControlScreen.kt` | controlo + telemetria ao vivo |
| Mapa | `ui/screens/MapScreen.kt` | posição + rota ao vivo |
| Histórico | `ui/screens/HistoryScreen.kt` | lista de voos |
| Detalhe do voo | `ui/screens/FlightDetailScreen.kt` | stats + rota no mapa |

---

## Arquitetura

```
ui/         Compose + navigation-compose (ecrãs, tema, componentes)
viewmodel/  MVVM: AuthViewModel, DroneViewModel, FlightsViewModel, ControlViewModel
domain/     Outcome<T> (resultado de operações)
data/
  dto/      DTOs = contrato (Telemetry, Command, StatusRes, Backend, GeoJSON)
  api/      Retrofit (DroneApi @192.168.4.1, BackendApi configurável) + AuthInterceptor
  repo/     Repositórios (Auth, Backend, Drone)
  store/    DataStore (token, username, backendUrl)
di/         ServiceLocator (DI manual, sem Hilt para manter o MVP leve)
```

- **Rede:** Retrofit + Moshi (codegen KSP) + OkHttp (logging em debug). O `DroneApi`
  tem timeouts curtos (deteção rápida de perda de AP); o `BackendApi` é recriado a
  partir do URL atual a cada chamada.
- **ControlViewModel** é retido ao nível da Activity, por isso o ecrã de **Controlo**
  e o **Mapa** partilham a mesma telemetria ao vivo.

### Notas sobre osmdroid

- Mapa **OpenStreetMap, sem API key**. Define o `userAgent` no arranque
  (`Droninho32App`), como exige a política de uso dos servidores de tiles do OSM.
- Os tiles são descarregados da Internet e cacheados localmente pela biblioteca.
- O `MapView` é um `View` clássico embrulhado em `AndroidView`; o ciclo de vida
  (`onResume`/`onPause`/`onDetach`) é gerido por um `DisposableEffect`.

---

## Versões fixadas

| Componente | Versão |
|-----------|--------|
| Gradle | 8.7 |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 2.0.20 (+ plugin `kotlin.plugin.compose`) |
| KSP | 2.0.20-1.0.25 |
| Compose BOM | 2024.09.02 |
| Material 3 | (via BOM) |
| navigation-compose | 2.8.0 |
| Retrofit / converter-moshi | 2.11.0 |
| Moshi | 1.15.1 (codegen) |
| OkHttp / logging-interceptor | 4.12.0 |
| kotlinx-coroutines | 1.8.1 |
| lifecycle (viewmodel/runtime-compose) | 2.8.6 |
| datastore-preferences | 1.1.1 |
| osmdroid-android | 6.1.20 |
| minSdk / target / compileSdk | 26 / 34 / 34 |

---

## Permissões (AndroidManifest)

`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `ACCESS_FINE_LOCATION`,
`ACCESS_COARSE_LOCATION`. Tem `android:usesCleartextTraffic="true"` porque o drone
fala HTTP em `192.168.4.1` e o backend de desenvolvimento (`10.0.2.2:8000`) também.
Em produção, usar HTTPS e remover o cleartext.

---

## Limitações e próximos passos

- **Sem PID em malha fechada** nem controlo `rc` na UI — o MVP usa acelerador geral.
  O DTO `Command.rc(...)` já existe para evolução futura.
- **Vídeo (ESP-CAM)** não implementado.
- **Repetição autónoma de rotas** (waypoints) não implementada; as rotas guardadas
  do backend já têm DTO (`Route`).
- **Voos sem conta** ficam só em memória (não há para onde enviar). Persistência
  local offline (Room) é uma melhoria futura.
- **WebSocket de telemetria** (5–10 Hz) é futuro; o MVP faz polling a ~3 Hz.

---

## Licença

Ver [`LICENSE.md`](LICENSE.md) (PolyForm Noncommercial 1.0.0).
