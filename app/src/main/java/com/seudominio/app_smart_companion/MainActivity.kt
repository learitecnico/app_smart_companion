package com.seudominio.app_smart_companion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.vuzix.hud.actionmenu.ActionMenuActivity
import com.seudominio.app_smart_companion.service.WebRTCService
import com.seudominio.app_smart_companion.webrtc.WebRTCManager

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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "MainActivity onCreate - ActionMenuActivity initialized")
        
        // Initialize WebRTC
        WebRTCManager.initialize(this)
        
        // Check and request permissions
        checkPermissionsAndStartService()
    }
    
    private fun checkPermissionsAndStartService() {
        val deniedPermissions = REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (deniedPermissions.isEmpty()) {
            Log.d(TAG, "All permissions granted - starting WebRTC service")
            startWebRTCService()
        } else {
            Log.d(TAG, "Requesting permissions: ${deniedPermissions.joinToString()}")
            ActivityCompat.requestPermissions(this, deniedPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
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
                Log.d(TAG, "All permissions granted - starting WebRTC service")
                Toast.makeText(this, "Permissions granted - starting service", Toast.LENGTH_SHORT).show()
                startWebRTCService()
            } else {
                Log.w(TAG, "Some permissions denied")
                Toast.makeText(this, "All permissions are required for proper functionality", Toast.LENGTH_LONG).show()
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
        WebRTCManager.dispose()
    }
    
    // ===============================
    // VUZIX ACTION MENU METHODS
    // ===============================
    
    override fun onCreateActionMenu(menu: android.view.Menu?): Boolean {
        super.onCreateActionMenu(menu)  // CRITICAL: Must call parent first
        menuInflater.inflate(R.menu.main_menu, menu)
        Log.d(TAG, "Action menu created with ${menu?.size()} items")
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
        // TODO: Implementar controle de áudio via WebRTCService
        Toast.makeText(this, "Audio toggle - Implementation pending", Toast.LENGTH_SHORT).show()
    }
    
    private fun takeSnapshot() {
        Log.d(TAG, "Snapshot requested via Action Menu")
        // TODO: Implementar snapshot via WebRTCService
        Toast.makeText(this, "Snapshot capture - Implementation pending", Toast.LENGTH_SHORT).show()
    }
    
    private fun showConnectionStatus() {
        Log.d(TAG, "Connection status requested via Action Menu")
        // TODO: Verificar status da conexão WebRTC
        Toast.makeText(this, "Status: Connection check - Implementation pending", Toast.LENGTH_SHORT).show()
    }
    
    private fun openSettings() {
        Log.d(TAG, "Settings requested via Action Menu")
        // TODO: Implementar tela de configurações
        Toast.makeText(this, "Settings - Implementation pending", Toast.LENGTH_SHORT).show()
    }
}
