package com.fieldweather.recorder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fieldweather.recorder.ui.theme.*
import com.fieldweather.recorder.viewmodel.WeatherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(viewModel: WeatherViewModel) {
    val serverUrl by viewModel.settings.serverUrl.collectAsState()
    val timezone by viewModel.settings.timezone.collectAsState()
    val use24HourFormat by viewModel.settings.use24HourFormat.collectAsState()

    var showClearDataDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("APP SETTINGS", color = FieldWhite, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(16.dp))

        // Server URL
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { viewModel.settings.setServerUrl(it) },
            label = { Text("Server IP / Hub URL", color = FieldYellow) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = FieldYellow,
                unfocusedBorderColor = FieldWhite,
                focusedTextColor = FieldWhite,
                unfocusedTextColor = FieldWhite
            ),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Example: 192.168.1.10:8888", color = FieldGrey, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(24.dp))

        // Timezone Dropdown (Simplified for now)
        var expanded by remember { mutableStateOf(false) }
        val timezones = listOf("Device Default", "UTC", "EST", "CST", "MST", "PST")
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = timezone,
                onValueChange = {},
                readOnly = true,
                label = { Text("Log Timezone", color = FieldYellow) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = FieldYellow,
                    unfocusedBorderColor = FieldWhite,
                    focusedTextColor = FieldWhite,
                    unfocusedTextColor = FieldWhite
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                timezones.forEach { tz ->
                    DropdownMenuItem(
                        text = { Text(tz) },
                        onClick = {
                            viewModel.settings.setTimezone(tz)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 24 Hour Format Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, FieldWhite)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Use 24-Hour Time Format", color = FieldWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Switch(
                checked = use24HourFormat,
                onCheckedChange = { viewModel.settings.setUse24HourFormat(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = FieldBlack,
                    checkedTrackColor = FieldYellow,
                    uncheckedThumbColor = FieldBlack,
                    uncheckedTrackColor = FieldGrey
                )
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Clear Data Button
        Button(
            onClick = { showClearDataDialog = true },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = FieldWhite),
            shape = androidx.compose.ui.graphics.RectangleShape,
            border = BorderStroke(3.dp, FieldBlack)
        ) {
            Text("⚠️ WIPE LOCAL DATABASE", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Wipe All Data?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete all weather logs from this device and clear your active location. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.wipeLocalData()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text("Cancel") }
            }
        )
    }
}
