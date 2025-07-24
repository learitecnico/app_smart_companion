package com.seudominio.app_smart_companion

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.vuzix.hud.actionmenu.ActionMenuActivity
import com.seudominio.app_smart_companion.service.WebRTCService
import com.seudominio.app_smart_companion.webrtc.WebRTCManager
import com.seudominio.app_smart_companion.ui.HudDisplayManager
import com.seudominio.app_smart_companion.ui.HudMessageHandler
import org.json.JSONObject

class MainActivity : ActionMenuActivity() {
    companion object {
        const val TAG = "SmartCompanion"
        const val PERMISSION_REQUEST_CODE = 1001
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
    
    // HUD Display Components
    private lateinit var hudDisplayManager: HudDisplayManager
    private lateinit var hudMessageHandler: HudMessageHandler
    
    // UI Components
    private lateinit var hudTextDisplay: TextView
    private lateinit var connectionStatus: TextView
    private lateinit var processingIndicator: TextView
    
    // Broadcast Receiver for WebRTCService communication
    private var messageReceiver: BroadcastReceiver? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        Log.d(TAG, "MainActivity onCreate - ActionMenuActivity initialized")
        
        // Initialize UI components
        initializeHudComponents()
        
        // Initialize broadcast receiver
        setupBroadcastReceivers()
        
        // Initialize WebRTC
        WebRTCManager.initialize(this)
        
        // Check permissions but don't start service automatically
        checkPermissions()
    }
    
    /**
     * Initialize HUD display components
     */
    private fun initializeHudComponents() {
        // Find UI components
        hudTextDisplay = findViewById(R.id.hud_text_display)
        connectionStatus = findViewById(R.id.connection_status)
        processingIndicator = findViewById(R.id.processing_indicator)
        
        // Initialize HUD Display Manager
        hudDisplayManager = HudDisplayManager(
            hudTextView = hudTextDisplay,
            connectionStatusView = connectionStatus,
            processingIndicator = processingIndicator
        )
        
        // Initialize Message Handler
        hudMessageHandler = HudMessageHandler(
            hudDisplayManager = hudDisplayManager,
            context = this,
            onSendMessage = { message -> sendMessageToCompanion(message) }
        )
        
        // Show initial status
        hudDisplayManager.showStatusMessage("Smart Companion initializing...")
        hudDisplayManager.updateConnectionStatus(false)
        
        Log.d(TAG, "HUD components initialized")
    }
    
    /**
     * Setup broadcast receivers for WebRTCService communication
     */
    private fun setupBroadcastReceivers() {
        messageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.seudominio.app_smart_companion.HUD_MESSAGE" -> {
                        val message = intent.getStringExtra("message")
                        if (message != null) {
                            Log.d(TAG, "📱 HUD message received from WebRTCService")
                            handleCompanionMessage(message)
                        }
                    }
                    
                    "com.seudominio.app_smart_companion.CONNECTION_STATUS" -> {
                        val connected = intent.getBooleanExtra("connected", false)
                        Log.d(TAG, "📱 Connection status received from WebRTCService: $connected")
                        updateConnectionStatus(connected)
                    }
                }
            }
        }
        
        // Register broadcast receiver
        val filter = IntentFilter().apply {
            addAction("com.seudominio.app_smart_companion.HUD_MESSAGE")
            addAction("com.seudominio.app_smart_companion.CONNECTION_STATUS")
        }
        
        registerReceiver(messageReceiver, filter)
        Log.d(TAG, "Broadcast receivers registered")
    }
    
    private fun checkPermissions() {
        val deniedPermissions = REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (deniedPermissions.isEmpty()) {
            Log.d(TAG, "All permissions granted - ready to connect")
            hudDisplayManager.showStatusMessage("✅ Ready to connect\n📱 TAP TRACKPAD to open menu\n🔵 Select 'Conectar Companion'", 8000L)
        } else {
            Log.d(TAG, "Requesting permissions: ${deniedPermissions.joinToString()}")
            ActivityCompat.requestPermissions(this, deniedPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }
    
    private fun startConnection() {
        Log.d(TAG, "Starting WebRTC service and connection")
        hudDisplayManager.showStatusMessage("🔄 Starting connection...", 2000L)
        startWebRTCService()
    }
    
    private fun stopConnection() {
        Log.d(TAG, "Stopping WebRTC service and connection")
        hudDisplayManager.showStatusMessage("⏹️ Stopping connection...", 2000L)
        stopWebRTCService()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            if (allGranted) {
                Log.d(TAG, "All permissions granted - ready to connect")
                Toast.makeText(this, "Permissions granted - use menu to start connection", Toast.LENGTH_SHORT).show()
                hudDisplayManager.showStatusMessage("✅ Ready to connect\n📱 Use menu to start connection", 5000L)
            } else {
                Log.w(TAG, "Some permissions denied")
                Toast.makeText(this, "All permissions are required for proper functionality", Toast.LENGTH_LONG).show()
                hudDisplayManager.showStatusMessage("❌ Permissions required", 3000L)
            }
        }
    }
    
    private fun startWebRTCService() {
        val intent = Intent(this, WebRTCService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Log.d(TAG, "WebRTC service started")
    }
    
    private fun stopWebRTCService() {
        val intent = Intent(this, WebRTCService::class.java)
        stopService(intent)
        Log.d(TAG, "WebRTC service stopped")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Cleanup broadcast receiver
        messageReceiver?.let { receiver ->
            unregisterReceiver(receiver)
            messageReceiver = null
            Log.d(TAG, "Broadcast receiver unregistered")
        }
        
        // Cleanup HUD components
        if (::hudDisplayManager.isInitialized) {
            hudDisplayManager.cleanup()
        }
        
        WebRTCManager.dispose()
        Log.d(TAG, "MainActivity destroyed and cleaned up")
    }
    
    // ===============================
    // HUD MESSAGE HANDLING METHODS
    // ===============================
    
    /**
     * Handle incoming message from companion desktop (called by WebRTCService)
     */
    fun handleCompanionMessage(messageJson: String) {
        if (::hudMessageHandler.isInitialized) {
            hudMessageHandler.handleMessage(messageJson)
        } else {
            Log.w(TAG, "HUD message handler not initialized, ignoring message")
        }
    }
    
    /**
     * Send message to companion desktop (via WebRTCService)
     */
    private fun sendMessageToCompanion(message: JSONObject) {
        try {
            val messageString = message.toString()
            Log.d(TAG, "Sending message to companion: ${messageString.substring(0, minOf(100, messageString.length))}...")
            
            // Send message via WebSocket through WebRTCService
            val serviceIntent = Intent(this, WebRTCService::class.java).apply {
                action = "SEND_MESSAGE"
                putExtra("message", messageString)
            }
            startService(serviceIntent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message to companion", e)
        }
    }
    
    /**
     * Update connection status (called by WebRTCService)
     */
    fun updateConnectionStatus(connected: Boolean) {
        if (::hudDisplayManager.isInitialized) {
            hudDisplayManager.updateConnectionStatus(connected)
            
            if (connected) {
                hudDisplayManager.showStatusMessage("✅ Connected to companion", 2000L)
                // Request initial status
                if (::hudMessageHandler.isInitialized) {
                    hudMessageHandler.requestConnectionStatus()
                }
            } else {
                hudDisplayManager.showStatusMessage("❌ Connection lost", 3000L)
            }
        }
    }
    
    // ===============================
    // VUZIX ACTION MENU METHODS
    // ===============================
    
    override fun onCreateActionMenu(menu: android.view.Menu?): Boolean {
        super.onCreateActionMenu(menu)  // CRITICAL: Must call parent first
        menuInflater.inflate(R.menu.main_menu, menu)
        Log.d(TAG, "🎯 Action menu created with ${menu?.size()} items")
        
        // Debug: List all menu items
        menu?.let { m ->
            for (i in 0 until m.size()) {
                val item = m.getItem(i)
                Log.d(TAG, "🎯 Menu item $i: ${item.title} (id: ${item.itemId})")
            }
        }
        
        return true
    }
    
    override fun onActionItemSelected(item: android.view.MenuItem?): Boolean {
        Log.d(TAG, "🎯 Action item selected: ${item?.title} (id: ${item?.itemId})")
        return when (item?.itemId) {
            R.id.action_connect -> {
                Log.d(TAG, "🎯 CONNECT action selected!")
                startConnection()
                true
            }
            R.id.action_disconnect -> {
                stopConnection()
                true
            }
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
        
        if (::hudMessageHandler.isInitialized) {
            // Toggle processing indicator
            val currentlyProcessing = processingIndicator.visibility == android.view.View.VISIBLE
            hudMessageHandler.sendProcessingStatus(!currentlyProcessing, "Audio recording")
            
            hudDisplayManager.showStatusMessage(
                if (!currentlyProcessing) "🎤 Audio recording started" else "⏹️ Audio recording stopped", 
                2000L
            )
        } else {
            Toast.makeText(this, "HUD not initialized", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun takeSnapshot() {
        Log.d(TAG, "Snapshot requested via Action Menu")
        
        if (::hudDisplayManager.isInitialized) {
            hudDisplayManager.showStatusMessage("📸 Taking snapshot...", 2000L)
            
            // TODO: Integrate with CameraCapture via WebRTCService
            hudDisplayManager.showStatusMessage("📸 Snapshot captured", 2000L)
        } else {
            Toast.makeText(this, "HUD not initialized", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showConnectionStatus() {
        Log.d(TAG, "Connection status requested via Action Menu")
        
        if (::hudMessageHandler.isInitialized) {
            hudMessageHandler.requestConnectionStatus()
            hudDisplayManager.showStatusMessage("🔍 Checking connection status...", 2000L)
        } else {
            Toast.makeText(this, "HUD not initialized", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openSettings() {
        Log.d(TAG, "Settings requested via Action Menu")
        
        if (::hudDisplayManager.isInitialized) {
            // Show current HUD state for debugging
            val hudState = hudMessageHandler.getHudState()
            val statusText = "Current display: ${hudState.optInt("display_history_count", 0)} items"
            
            hudDisplayManager.showStatusMessage(statusText, 3000L)
            Toast.makeText(this, "Settings - HUD Debug Info", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Settings - HUD not initialized", Toast.LENGTH_SHORT).show()
        }
    }
}
