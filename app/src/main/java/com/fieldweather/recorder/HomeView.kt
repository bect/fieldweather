package com.fieldweather.recorder

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fieldweather.recorder.ui.theme.*
import com.fieldweather.recorder.viewmodel.WeatherViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
fun HomeView(viewModel: WeatherViewModel) {
    val timeUntilNextLog by viewModel.timeUntilNextLog.collectAsState()
    
    if (timeUntilNextLog != null && timeUntilNextLog!! > 0) {
        CountdownView(timeUntilNextLog!!)
    } else {
        LogWeatherForm(viewModel)
    }
}

@Composable
fun CountdownView(timeMillis: Long) {
    val hours = timeMillis / (1000 * 60 * 60)
    val minutes = (timeMillis % (1000 * 60 * 60)) / (1000 * 60)
    
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("NEXT LOG AVAILABLE IN", color = FieldGrey, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("${hours}h ${minutes}m", fontSize = 48.sp, color = FieldYellow, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("You must wait 2 hours between weather logs.", color = FieldWhite, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWeatherForm(viewModel: WeatherViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var totalMinutesAgo by remember { mutableStateOf(0) }
    var selectedCondition by remember { mutableStateOf<String?>(null) }
    
    val use24HourFormat by viewModel.settings.use24HourFormat.collectAsState()
    val timezone by viewModel.settings.timezone.collectAsState()
    
    val conditions = listOf(
        "Sunny" to "☀️",
        "Cloudy" to "☁️",
        "Raining" to "🌧️",
        "Stormy" to "⛈️",
        "Windy" to "🌬️",
        "Foggy" to "🌫️"
    )


    Column(modifier = Modifier.fillMaxSize()) {
        // Top Section - Time Observed
        Column(modifier = Modifier.padding(20.dp)) {
            Text("TIME OBSERVED", color = FieldBlack, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, fontSize = 12.sp, modifier = Modifier.background(FieldWhite).padding(horizontal = 8.dp, vertical = 4.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                TimeChip("↺ NOW", FieldYellow) { totalMinutesAgo = 0 }
                TimeChip("- 5m", FieldWhite) { totalMinutesAgo += 5 }
                TimeChip("- 15m", FieldWhite) { totalMinutesAgo += 15 }
                TimeChip("- 30m", FieldWhite) { totalMinutesAgo += 30 }
                TimeChip("- 1h", FieldWhite) { totalMinutesAgo += 60 }
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            val timeStr = remember(totalMinutesAgo, timezone, use24HourFormat) {
                val observedTimeMillis = System.currentTimeMillis() - (totalMinutesAgo * 60000L)
                val zoneId = if (timezone == "Device Default") ZoneId.systemDefault() else ZoneId.of(timezone.replace("EST", "-05:00").replace("CST", "-06:00").replace("MST", "-07:00").replace("PST", "-08:00").replace("UTC", "UTC"))
                val pattern = if (use24HourFormat) "HH:mm" else "hh:mm a"
                Instant.ofEpochMilli(observedTimeMillis).atZone(zoneId).format(DateTimeFormatter.ofPattern(pattern))
            }
            val subText = if (totalMinutesAgo == 0) "(Now)" else "(${totalMinutesAgo / 60}h ${totalMinutesAgo % 60}m ago)"
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(3.dp, FieldBlack)
                    .background(FieldBlack)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Will Save As".uppercase(), color = Color(0xFFCCCCCC), fontSize = 12.sp)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(timeStr, color = FieldYellow, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text(subText, color = FieldWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Bottom Section - Conditions
        Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
            Text("CURRENT CONDITIONS", color = FieldBlack, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, fontSize = 12.sp, modifier = Modifier.background(FieldWhite).padding(horizontal = 8.dp, vertical = 4.dp))
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(conditions) { condition ->
                    val isSelected = selectedCondition == condition.first
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCondition = condition.first },
                        border = BorderStroke(3.dp, FieldBlack),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) FieldBlack else FieldWhite)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(condition.second, fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                condition.first.uppercase(),
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) FieldYellow else FieldBlack,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Action Bar
        Box(modifier = Modifier.fillMaxWidth().background(FieldWhite).border(BorderStroke(4.dp, FieldBlack), shape = androidx.compose.ui.graphics.RectangleShape).padding(20.dp)) {
            val activeLocation by viewModel.activeLocation.collectAsState(initial = null)
            
            Button(
                onClick = {
                    if (selectedCondition == null) {
                        Toast.makeText(context, "Select a weather condition", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (activeLocation == null) {
                        Toast.makeText(context, "Please select a location from the top header first!", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    
                    val observedTimeMillis = System.currentTimeMillis() - (totalMinutesAgo * 60000L)
                    viewModel.saveRecord(selectedCondition!!, activeLocation!!, observedTimeMillis)
                    totalMinutesAgo = 0
                    selectedCondition = null
                    Toast.makeText(context, "Weather Logged!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FieldYellow, contentColor = FieldBlack),
                shape = androidx.compose.ui.graphics.RectangleShape,
                border = BorderStroke(3.dp, FieldBlack)
            ) {
                Text("💾 SAVE RECORD", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun RowScope.TimeChip(text: String, bgColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .border(3.dp, FieldBlack)
            .background(bgColor)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = FontWeight.ExtraBold, color = FieldBlack, fontSize = 14.sp)
    }
}
