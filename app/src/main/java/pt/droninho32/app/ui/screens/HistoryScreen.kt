package pt.droninho32.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.droninho32.app.R
import pt.droninho32.app.data.dto.Flight
import pt.droninho32.app.ui.components.LabelValueRow
import pt.droninho32.app.ui.components.LoadingBox
import pt.droninho32.app.ui.components.SectionCard
import pt.droninho32.app.viewmodel.FlightsViewModel

/** Histórico de voos (recurso do backend). Toca num voo para abrir o detalhe. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    vm: FlightsViewModel,
    onOpenFlight: (Int) -> Unit,
) {
    val state by vm.list.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refreshList() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) },
    ) { padding ->
        when {
            state.loading && state.flights.isEmpty() -> LoadingBox(Modifier.padding(padding))
            else -> Column(Modifier.padding(padding).fillMaxSize()) {
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
                if (state.flights.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.history_empty), textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.flights, key = { it.id }) { flight ->
                            FlightRow(flight) { onOpenFlight(flight.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlightRow(flight: Flight, onClick: () -> Unit) {
    SectionCard(
        title = flight.startedAt ?: "Voo #${flight.id}",
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        flight.droneName?.let { LabelValueRow(stringResource(R.string.drones_name), it) }
        LabelValueRow(stringResource(R.string.flight_distance), "%.0f m".format(flight.distanceM))
        LabelValueRow(stringResource(R.string.flight_duration), "%.0f s".format(flight.durationS))
        LabelValueRow(stringResource(R.string.flight_points), flight.points.toString())
    }
}
