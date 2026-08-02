package io.github.bect.fieldweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bect.fieldweather.ui.theme.*
import io.github.bect.fieldweather.viewmodel.WeatherViewModel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val viewModel: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FieldWeatherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionScreen {
                        AppContent(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionScreen(content: @Composable () -> Unit) {
    val context = LocalContext.current
    
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var showSettingsRedirect by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasPermission = granted
        if (!granted) {
            showSettingsRedirect = true
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    if (hasPermission) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "PERMISSIONS REQUIRED",
                color = FieldBlack,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "FieldWeather needs your location to accurately tag your weather reports. Please grant location access to continue.",
                color = FieldGrey,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    if (showSettingsRedirect) {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = FieldYellow, contentColor = FieldBlack),
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Text(
                    text = if (showSettingsRedirect) "OPEN SETTINGS" else "GRANT PERMISSION",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(viewModel: WeatherViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    
    val activeLocation by viewModel.activeLocation.collectAsState(initial = null)
    val knownLocations by viewModel.knownLocations.collectAsState(initial = emptyList())
    
    var showLocationDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Navigation
        Row(modifier = Modifier.fillMaxWidth().background(FieldBlack)) {
            TabButton("Log", selectedTab == 0, Modifier.weight(1f)) { selectedTab = 0 }
            TabButton("Stats", selectedTab == 1, Modifier.weight(1f)) { selectedTab = 1 }
            TabButton("⚙️", selectedTab == 2, Modifier.weight(0.5f)) { selectedTab = 2 }
        }

        // Location Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FieldBlack)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("FIELD WEATHER", color = FieldWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(
                "📍 ${activeLocation?.locationName ?: "SELECT LOCATION"}", 
                color = FieldYellow, 
                fontWeight = FontWeight.ExtraBold, 
                fontSize = 12.sp,
                modifier = Modifier.clickable { showLocationDialog = true }
            )
        }
        HorizontalDivider(color = FieldYellow, thickness = 4.dp)

        // View Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                0 -> HomeView(viewModel)
                1 -> StatsView(viewModel)
                2 -> SettingsView(viewModel)
            }
        }
    }
    
    if (showLocationDialog) {
        LocationSelectionDialog(
            viewModel = viewModel,
            knownLocations = knownLocations,
            onDismiss = { showLocationDialog = false },
            onSelect = { 
                viewModel.setActiveLocation(it)
                showLocationDialog = false 
            }
        )
    }
}

@Composable
fun TabButton(text: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier.padding(16.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (isSelected) FieldYellow else Color.Gray
        )
    ) {
        Text(
            text = text.uppercase(),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )
    }
}
