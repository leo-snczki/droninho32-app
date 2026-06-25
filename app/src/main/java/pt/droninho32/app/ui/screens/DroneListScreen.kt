package pt.droninho32.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import pt.droninho32.app.R
import pt.droninho32.app.data.dto.Drone
import pt.droninho32.app.ui.components.LoadingBox
import pt.droninho32.app.ui.components.SectionCard
import pt.droninho32.app.viewmodel.DroneViewModel

/** Lista de drones do utilizador, com registo de novo drone e atalho para o controlo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DroneListScreen(
    vm: DroneViewModel,
    onOpenControl: (Drone) -> Unit,
    onLogout: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drones_title)) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.drones_logout))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.drones_add))
            }
        },
    ) { padding ->
        when {
            state.loading && state.drones.isEmpty() -> LoadingBox(Modifier.padding(padding))
            else -> Column(Modifier.padding(padding).fillMaxSize()) {
                state.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                if (state.drones.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.drones_empty), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                } else {
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.drones, key = { it.id }) { drone ->
                            DroneRow(
                                drone = drone,
                                onOpen = { onOpenControl(drone) },
                                onDelete = { vm.deleteDrone(drone.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddDroneDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, model, fw ->
                vm.addDrone(name, model, fw) { showAdd = false }
            },
        )
    }
}

@Composable
private fun DroneRow(drone: Drone, onOpen: () -> Unit, onDelete: () -> Unit) {
    SectionCard(title = drone.name) {
        if (drone.model.isNotBlank()) {
            Text("${stringResource(R.string.drones_model)}: ${drone.model}", style = MaterialTheme.typography.bodyLarge)
        }
        if (drone.firmwareVersion.isNotBlank()) {
            Text("${stringResource(R.string.drones_firmware)}: ${drone.firmwareVersion}", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onOpen) { Text(stringResource(R.string.drones_open_control)) }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Apagar", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddDroneDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var fw by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drones_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.drones_name)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text(stringResource(R.string.drones_model)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = fw, onValueChange = { fw = it },
                    label = { Text(stringResource(R.string.drones_firmware)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, model, fw) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
