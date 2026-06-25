package pt.droninho32.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.droninho32.app.R
import pt.droninho32.app.data.dto.Stats
import pt.droninho32.app.ui.components.LabelValueRow
import pt.droninho32.app.ui.components.LoadingBox
import pt.droninho32.app.ui.components.OsmMap
import pt.droninho32.app.ui.components.SectionCard
import pt.droninho32.app.viewmodel.FlightsViewModel

/** Detalhe de um voo: estatísticas + rota desenhada no mapa. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightDetailScreen(
    flightId: Int,
    vm: FlightsViewModel,
    onBack: () -> Unit,
) {
    val state by vm.detail.collectAsStateWithLifecycle()

    LaunchedEffect(flightId) { vm.loadDetail(flightId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.flight_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.error != null -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text(state.error!!, color = MaterialTheme.colorScheme.error) }

            else -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatsCard(state.stats)

                // Mapa com a rota do voo (sem ponto ao vivo).
                SectionCard(title = stringResource(R.string.flight_view_route)) {
                    Box(Modifier.fillMaxWidth().height(320.dp)) {
                        if (state.routePoints.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.map_no_fix))
                            }
                        } else {
                            OsmMap(
                                modifier = Modifier.fillMaxSize(),
                                route = state.routePoints,
                                livePoint = null,
                                follow = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(stats: Stats?) {
    SectionCard(title = stringResource(R.string.flight_status)) {
        if (stats == null) {
            Text(stringResource(R.string.loading))
            return@SectionCard
        }
        LabelValueRow(stringResource(R.string.flight_distance), "%.0f m".format(stats.distanceM))
        LabelValueRow(stringResource(R.string.flight_duration), "%.0f s".format(stats.durationS))
        LabelValueRow(stringResource(R.string.flight_max_alt), "%.1f m".format(stats.maxAltM))
        LabelValueRow(stringResource(R.string.flight_max_speed), "%.1f m/s".format(stats.maxSpeedMps))
        LabelValueRow(stringResource(R.string.flight_avg_speed), "%.1f m/s".format(stats.avgSpeedMps))
        LabelValueRow(stringResource(R.string.flight_points), stats.points.toString())
    }
}
