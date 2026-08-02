package io.github.bect.fieldweather

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bect.fieldweather.data.WeatherRecord
import io.github.bect.fieldweather.ui.theme.*
import io.github.bect.fieldweather.viewmodel.DayColor
import io.github.bect.fieldweather.viewmodel.SyncState
import io.github.bect.fieldweather.viewmodel.WeatherViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
private val timeFormatter12 = DateTimeFormatter.ofPattern("hh:mm a")
private val timeFormatter24 = DateTimeFormatter.ofPattern("HH:mm")
private val lastSyncedDateFormatter12 = DateTimeFormatter.ofPattern("MMM dd, yyyy - hh:mm a")
private val lastSyncedDateFormatter24 = DateTimeFormatter.ofPattern("MMM dd, yyyy - HH:mm")
@Composable
fun StatsView(viewModel: WeatherViewModel) {
    val records by viewModel.allRecords.collectAsState(initial = emptyList())
    val thirtyDayStrip by viewModel.thirtyDayStrip.collectAsState(initial = List(30) { DayColor.NONE })
    val rainDays by viewModel.rainDays.collectAsState()
    val sunnyDays by viewModel.sunnyDays.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val lastSyncedDate by viewModel.lastSyncedDate.collectAsState()
    
    val use24HourFormat by viewModel.settings.use24HourFormat.collectAsState()
    val timezone by viewModel.settings.timezone.collectAsState()
    val zoneId = if (timezone == "Device Default") ZoneId.systemDefault() else ZoneId.of(timezone.replace("EST", "-05:00").replace("CST", "-06:00").replace("MST", "-07:00").replace("PST", "-08:00").replace("UTC", "UTC"))

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("SUMMARY", color = FieldWhite, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            
            Box(modifier = Modifier.weight(1f).border(BorderStroke(3.dp, FieldBlack)).background(FieldWhite).padding(12.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(rainDays.toString(), color = FieldBlue, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp)
                    Text("RAIN DAYS", color = Color(0xFF444444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f).border(BorderStroke(3.dp, FieldBlack)).background(FieldWhite).padding(12.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(sunnyDays.toString(), color = FieldOrange, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp)
                    Text("SUNNY DAYS", color = Color(0xFF444444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("LAST 30 DAYS", color = FieldWhite, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(15),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            items(thirtyDayStrip) { color ->
                val bgColor = when(color) {
                    DayColor.SUNNY -> FieldYellow
                    DayColor.CLOUDY -> FieldGrey
                    DayColor.RAIN -> FieldBlue
                    DayColor.NONE -> Color(0xFFE0E0E0)
                }
                Box(modifier = Modifier.height(24.dp).border(1.dp, FieldBlack).background(bgColor))
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LegendItem(color = FieldYellow, label = "Sun")
            LegendItem(color = FieldGrey, label = "Cloud")
            LegendItem(color = FieldBlue, label = "Rain")
            LegendItem(color = Color(0xFFE0E0E0), label = "None")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("RECENT LOGS", color = FieldWhite, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(records) { record ->
                RecordItem(record, zoneId, use24HourFormat)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val pendingCount = records.count { !it.isSynced }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .border(2.dp, FieldBlack, shape = androidx.compose.foundation.shape.CircleShape)
                    .background(if (pendingCount > 0) FieldOrange else FieldGreen, shape = androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            val message = if (pendingCount > 0) "$pendingCount Records Pending Sync" else "All Records Synced"
            Text(
                message.uppercase(),
                color = FieldWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        if (lastSyncedDate != null) {
            val formatter = if (use24HourFormat) lastSyncedDateFormatter24 else lastSyncedDateFormatter12
            val formattedDate = Instant.parse(lastSyncedDate).atZone(zoneId).format(formatter)
            Text(
                text = "Last Synced: $formattedDate",
                color = FieldGrey,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        val btnText = when (syncState) {
            is SyncState.Syncing -> "⏳ SYNCING..."
            is SyncState.Success -> "✅ SYNC COMPLETE"
            is SyncState.Error -> "🔄 RETRY SYNC"
            else -> "🔄 SYNC TO LOCAL HUB"
        }
        val btnColor = if (syncState is SyncState.Syncing) FieldBlack else FieldWhite
        val textColor = if (syncState is SyncState.Syncing) FieldYellow else FieldBlack

        Button(
            onClick = { viewModel.syncData() },
            enabled = syncState !is SyncState.Syncing,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = btnColor, contentColor = textColor, disabledContainerColor = FieldBlack, disabledContentColor = FieldYellow),
            shape = androidx.compose.ui.graphics.RectangleShape,
            border = BorderStroke(3.dp, FieldBlack)
        ) {
            Text(btnText, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
        
        if (syncState is SyncState.Error) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = (syncState as SyncState.Error).message,
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun SummaryCard(modifier: Modifier, count: Int, label: String, color: Color) {
    Column(
        modifier = modifier
            .border(3.dp, FieldBlack)
            .background(FieldWhite)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(count.toString(), color = color, fontSize = 36.sp, fontWeight = FontWeight.Black)
        Text(label.uppercase(), color = Color(0xFF444444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).border(1.dp, FieldBlack).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = FieldWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RecordItem(record: WeatherRecord, zoneId: ZoneId, use24HourFormat: Boolean) {
    val (dateStr, timeStr) = androidx.compose.runtime.remember(record.timestamp, zoneId, use24HourFormat) {
        val zdt = Instant.parse(record.timestamp).atZone(zoneId)
        val dStr = zdt.format(dateFormatter)
        val formatter = if (use24HourFormat) timeFormatter24 else timeFormatter12
        val tStr = zdt.format(formatter)
        dStr to tStr
    }
    
    val emoji = when (record.condition) {
        "Sunny" -> "☀️"
        "Cloudy" -> "☁️"
        "Raining" -> "🌧️"
        "Stormy" -> "⛈️"
        "Windy" -> "🌬️"
        "Foggy" -> "🌫️"
        else -> "❓"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .border(BorderStroke(2.dp, FieldBlack), shape = androidx.compose.ui.graphics.RectangleShape)
            .background(FieldWhite)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 28.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(record.condition.uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = FieldBlack)
                Text(dateStr, fontSize = 12.sp, color = Color(0xFF555555), fontWeight = FontWeight.SemiBold)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(timeStr, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = FieldBlack)
            Text("📍 ${record.locationName}", fontSize = 12.sp, color = Color(0xFF555555), fontWeight = FontWeight.SemiBold)
        }
    }
}
