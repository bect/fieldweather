package com.fieldweather.recorder

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fieldweather.recorder.data.SavedLocation
import com.fieldweather.recorder.ui.theme.*
import com.fieldweather.recorder.viewmodel.WeatherViewModel
import kotlinx.coroutines.launch

@Composable
fun LocationSelectionDialog(
    viewModel: WeatherViewModel,
    knownLocations: List<SavedLocation>,
    onDismiss: () -> Unit,
    onSelect: (SavedLocation) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var showNewLocationInput by remember { mutableStateOf(false) }
    var newLocationName by remember { mutableStateOf("") }
    var fetchingGps by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            fetchingGps = true
            coroutineScope.launch {
                val loc = viewModel.fetchLocation()
                if (loc != null) {
                    onSelect(SavedLocation(newLocationName, loc.latitude, loc.longitude))
                } else {
                    Toast.makeText(context, "Failed to get location", Toast.LENGTH_SHORT).show()
                }
                fetchingGps = false
            }
        } else {
            Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Global Location", fontWeight = FontWeight.Bold) },
        text = {
            if (showNewLocationInput) {
                Column {
                    OutlinedTextField(
                        value = newLocationName,
                        onValueChange = { newLocationName = it },
                        label = { Text("New Location Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (newLocationName.isNotBlank()) {
                                val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    fetchingGps = true
                                    coroutineScope.launch {
                                        val loc = viewModel.fetchLocation()
                                        if (loc != null) {
                                            onSelect(SavedLocation(newLocationName, loc.latitude, loc.longitude))
                                        } else {
                                            Toast.makeText(context, "Failed to get location", Toast.LENGTH_SHORT).show()
                                        }
                                        fetchingGps = false
                                    }
                                } else {
                                    locationPermissionLauncher.launch(permissions)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !fetchingGps
                    ) {
                        Text(if (fetchingGps) "Fetching GPS..." else "📍 Fetch GPS & Save")
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(knownLocations) { loc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(loc) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📍", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(loc.locationName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Divider()
                    }
                    item {
                        TextButton(
                            onClick = { showNewLocationInput = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text("+ Add New Location")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
