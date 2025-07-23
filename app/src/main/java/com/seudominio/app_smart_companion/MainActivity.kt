package com.seudominio.app_smart_companion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.vuzix.hud.actionmenu.ActionMenuActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.seudominio.app_smart_companion.service.WebRTCService
import com.seudominio.app_smart_companion.ui.theme.App_smart_companionTheme
import com.seudominio.app_smart_companion.ui.HudOverlay
import com.seudominio.app_smart_companion.ui.HudOverlayManager
import com.seudominio.app_smart_companion.webrtc.WebRTCManager
import kotlinx.coroutines.launch

class MainActivity : ActionMenuActivity() {
    
    companion object {
        const val TAG = "MainActivity"
        val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize WebRTC
        WebRTCManager.initialize(this)
        
        setContent {
            App_smart_companionTheme {
                MainScreen()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        WebRTCManager.dispose()
    }
    
    // ===============================
    // VUZIX ACTION MENU METHODS
    // ===============================
    
    override fun onCreateActionMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        Log.d(TAG, "Action menu created")
        return true
    }
    
    override fun onActionItemSelected(item: android.view.MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.action_toggle_audio -> {
                toggleAudioCapture()
                true
            }
            R.id.action_take_snapshot -> {
                takeSnapshot()
                true
            }
            R.id.action_connection_status -> {
                showConnectionStatus()
                true
            }
            R.id.action_settings -> {
                openSettings()
                true
            }
            else -> super.onActionItemSelected(item)
        }
    }
    
    private fun toggleAudioCapture() {
        Log.d(TAG, "Toggle audio capture requested via Action Menu")
        // Implementar controle de áudio via menu
        Toast.makeText(this, "Audio toggle requested", Toast.LENGTH_SHORT).show()
    }
    
    private fun takeSnapshot() {
        Log.d(TAG, "Snapshot requested via Action Menu")
        // Implementar snapshot via menu  
        Toast.makeText(this, "Snapshot requested", Toast.LENGTH_SHORT).show()
    }
    
    private fun showConnectionStatus() {
        Log.d(TAG, "Connection status requested via Action Menu")
        // Mostrar status da conexão
        Toast.makeText(this, "Status: Checking connection...", Toast.LENGTH_SHORT).show()
    }
    
    private fun openSettings() {
        Log.d(TAG, "Settings requested via Action Menu")
        // Abrir configurações
        Toast.makeText(this, "Settings opened", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isServiceRunning by remember { mutableStateOf(false) }
    var allPermissionsGranted by remember { mutableStateOf(false) }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        allPermissionsGranted = permissions.all { it.value }
        if (!allPermissionsGranted) {
            Toast.makeText(
                context,
                "All permissions are required for the app to work properly",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // Check permissions on first composition
    LaunchedEffect(Unit) {
        allPermissionsGranted = MainActivity.REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Smart Companion",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Permissions:")
                        Text(
                            text = if (allPermissionsGranted) "Granted" else "Required",
                            color = if (allPermissionsGranted) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Service:")
                        Text(
                            text = if (isServiceRunning) "Running" else "Stopped",
                            color = if (isServiceRunning) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Control Buttons
            if (!allPermissionsGranted) {
                Button(
                    onClick = {
                        permissionLauncher.launch(MainActivity.REQUIRED_PERMISSIONS)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text("Grant Permissions")
                }
            } else {
                Button(
                    onClick = {
                        if (isServiceRunning) {
                            // Stop service
                            val intent = Intent(context, WebRTCService::class.java)
                            context.stopService(intent)
                            isServiceRunning = false
                        } else {
                            // Start service
                            val intent = Intent(context, WebRTCService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            isServiceRunning = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text(if (isServiceRunning) "Stop Service" else "Start Service")
                }
                
                Button(
                    onClick = {
                        // Take snapshot
                        scope.launch {
                            Log.d("MainActivity", "Snapshot requested")
                            // This will be handled by the service
                        }
                    },
                    enabled = isServiceRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Take Snapshot")
                }
            }
            
            // Instructions
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Instructions",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "1. Grant all required permissions\n" +
                              "2. Start the service to begin WebRTC connection\n" +
                              "3. Connect to the Companion app on your PC\n" +
                              "4. Speak to send audio or take snapshots",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}