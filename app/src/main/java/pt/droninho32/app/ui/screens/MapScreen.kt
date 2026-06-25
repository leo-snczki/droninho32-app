package pt.droninho32.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.osmdroid.util.GeoPoint
import pt.droninho32.app.R
import pt.droninho32.app.ui.components.OsmMap
import pt.droninho32.app.viewmodel.ControlViewModel

/**
 * Mapa ao vivo: mostra a posição atual do drone (quando há fix de GPS) e desenha o
 * rasto da rota acumulada na telemetria recebida durante a sessão de controlo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    vm: ControlViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Pede permissão de localização (para mostrar o utilizador; o drone vem por telemetria).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* opcional: não bloqueia o mapa */ }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    // Acumula o rasto localmente a partir das posições ao vivo.
    val trail = remember { mutableStateOf(listOf<GeoPoint>()) }
    LaunchedEffect(state.livePosition) {
        state.livePosition?.let { p ->
            val last = trail.value.lastOrNull()
            if (last == null || last.latitude != p.latitude || last.longitude != p.longitude) {
                trail.value = trail.value + p
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.map_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            OsmMap(
                modifier = Modifier.fillMaxSize(),
                route = trail.value,
                livePoint = state.livePosition,
                follow = true,
                markerTitle = stringResource(R.string.map_drone),
            )
            if (state.livePosition == null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                ) {
                    Text(
                        stringResource(R.string.map_no_fix),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
