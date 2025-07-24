package com.seudominio.app_smart_companion.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.seudominio.app_smart_companion.MainActivity
import com.seudominio.app_smart_companion.R
import com.seudominio.app_smart_companion.audio.AudioCapture
import com.seudominio.app_smart_companion.camera.CameraCapture
import com.seudominio.app_smart_companion.webrtc.DataChannelManager
import com.seudominio.app_smart_companion.webrtc.WebRTCManager
import com.seudominio.app_smart_companion.signaling.SignalingClient
import com.seudominio.app_smart_companion.config.AppConfig
import com.seudominio.app_smart_companion.ui.HudOverlayManager
import kotlinx.coroutines.*
import org.webrtc.*
import org.json.JSONObject
import java.nio.ByteBuffer

class WebRTCService : Service() {
    
    companion object {
        private const val TAG = "SmartCompanion" // Consistent logging
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "webrtc_service_channel"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dataChannelManager: DataChannelManager? = null
    private var audioCapture: AudioCapture? = null
    private var cameraCapture: CameraCapture? = null
    private var signalingClient: SignalingClient? = null
    private var hudOverlayManager: HudOverlayManager? = null
    private var currentPeerConnection: PeerConnection? = null
    private var isWebRTCInitialized = false
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        startForeground()
        initializeWebRTC()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        cleanup()
        serviceScope.cancel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WebRTC Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Smart Companion WebRTC Service"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForeground() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Companion")
            .setContentText("WebRTC service is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    private fun initializeWebRTC() {
        serviceScope.launch {
            try {
                // Initialize WebRTC Manager
                if (!WebRTCManager.isInitialized()) {
                    WebRTCManager.initialize(this@WebRTCService)
                }
                
                // Setup HUD overlay manager
                hudOverlayManager = HudOverlayManager(this@WebRTCService)
                
                // Setup audio capture
                setupAudioCapture()
                
                // Setup camera capture  
                setupCameraCapture()
                
                // Initialize signaling client
                setupSignalingClient()
                
                isWebRTCInitialized = true
                Log.d(TAG, "WebRTC initialized successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize WebRTC", e)
            }
        }
    }
    
    private fun setupSignalingClient() {
        signalingClient = SignalingClient(AppConfig.SIGNALING_URL, serviceScope).apply {
            setListener(object : SignalingClient.SignalingListener {
                override fun onConnected() {
                    Log.d(TAG, "Connected to signaling server")
                    hudOverlayManager?.updateConnectionStatus("Connected to Companion")
                    // Create peer connection when connected
                    createPeerConnection("companion-desktop")
                    // Auto-start audio capture when connected
                    startAudioCapture()
                    Log.d(TAG, "Audio capture started automatically")
                }
                
                override fun onDisconnected() {
                    Log.d(TAG, "Disconnected from signaling server")
                    hudOverlayManager?.updateConnectionStatus("Disconnected")
                }
                
                override fun onOfferReceived(offer: SessionDescription) {
                    Log.d(TAG, "Offer received from companion")
                    handleOffer(offer)
                }
                
                override fun onAnswerReceived(answer: SessionDescription) {
                    Log.d(TAG, "Answer received from companion")
                    handleAnswer(answer)
                }
                
                override fun onIceCandidateReceived(candidate: IceCandidate) {
                    Log.d(TAG, "ICE candidate received")
                    currentPeerConnection?.addIceCandidate(candidate)
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "Signaling error: $error")
                    hudOverlayManager?.updateStatus("Error: $error")
                }
            })
            
            // Connect to signaling server
            connect()
        }
    }
    
    private fun setupAudioCapture() {
        audioCapture = AudioCapture(this).apply {
            onAudioDataCaptured = { audioData ->
                // Send audio data to Companion Desktop via DataChannel for OpenAI processing
                Log.v(TAG, "Audio data captured: ${audioData.size} bytes")
                
                // Send audio via DataChannel to Companion Desktop
                sendAudioViaDataChannel(audioData)
            }
        }
    }
    
    private fun setupCameraCapture() {
        cameraCapture = CameraCapture(this).apply {
            onSnapshotCaptured = { imageData ->
                // Snapshot capturado e comprimido ≤200KB
                Log.d(TAG, "Snapshot ready: ${imageData.size} bytes")
                dataChannelManager?.sendSnapshot(imageData)
            }
            
            // Initialize camera
            initialize()
        }
    }
    
    private fun handleOffer(offer: SessionDescription) {
        currentPeerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set successfully")
                createAnswer()
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create session: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set remote description: $error")
            }
        }, offer)
    }
    
    private fun handleAnswer(answer: SessionDescription) {
        currentPeerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote answer set successfully")
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create session: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set remote answer: $error")
            }
        }, answer)
    }
    
    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        
        currentPeerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { answer ->
                    currentPeerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local answer set, sending to companion")
                            signalingClient?.sendAnswer(answer)
                        }
                        override fun onCreateFailure(error: String?) {
                            Log.e(TAG, "Failed to create local desc: $error")
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Failed to set local desc: $error")
                        }
                    }, answer)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create answer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }
    
    fun createPeerConnection(peerId: String) {
        if (!isWebRTCInitialized) {
            Log.w(TAG, "WebRTC not initialized yet")
            return
        }
        
        currentPeerConnection = WebRTCManager.createPeerConnection(peerId, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "ICE candidate generated: ${candidate.sdp}")
                signalingClient?.sendIceCandidate(candidate)
            }
            
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                Log.d(TAG, "ICE candidates removed")
            }
            
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "Signaling state changed: $state")
            }
            
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE connection state changed: $state")
            }
            
            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d(TAG, "ICE connection receiving change: $receiving")
            }
            
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "ICE gathering state changed: $state")
            }
            
            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "Stream added")
            }
            
            override fun onRemoveStream(stream: MediaStream?) {
                Log.d(TAG, "Stream removed")
            }
            
            override fun onDataChannel(dataChannel: DataChannel?) {
                Log.d(TAG, "Data channel received from remote peer")
                // For now, we'll handle incoming data channels here
                // In production, DataChannelManager should be created when WE create the peer connection
                dataChannel?.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(previousAmount: Long) {}
                    
                    override fun onStateChange() {
                        Log.d(TAG, "Remote DataChannel state: ${dataChannel.state()}")
                    }
                    
                    override fun onMessage(buffer: DataChannel.Buffer) {
                        // Handle incoming messages
                        val data = buffer.data
                        val bytes = ByteArray(data.remaining())
                        data.get(bytes)
                        val message = String(bytes)
                        Log.d(TAG, "DataChannel message received: $message")
                        
                        try {
                            val json = JSONObject(message)
                            val messageType = json.getString("type")
                            handleDataChannelMessage(messageType, json)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse DataChannel message", e)
                        }
                    }
                })
            }
            
            override fun onRenegotiationNeeded() {
                Log.d(TAG, "Renegotiation needed")
            }
            
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.d(TAG, "Track added")
            }
            
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
                Log.d(TAG, "Connection state changed: $state")
            }
        })
        
        // Create DataChannelManager after PeerConnection is created
        currentPeerConnection?.let { pc ->
            dataChannelManager = DataChannelManager(pc, serviceScope)
            Log.d(TAG, "DataChannelManager created for peer: $peerId")
        }
    }
    
    private fun handleDataChannelMessage(messageType: String, json: JSONObject) {
        when (messageType) {
            "capture_snapshot" -> {
                Log.d(TAG, "Snapshot request received")
                takePicture()
            }
            "model_text" -> {
                val text = json.optString("text", "")
                Log.d(TAG, "Model text received: $text")
                
                // Display text on HUD
                hudOverlayManager?.showText(text)
                hudOverlayManager?.updateStatus("Response received")
            }
            "model_audio" -> {
                Log.d(TAG, "Model audio response received")
                // TODO: Handle audio playback
            }
            else -> {
                Log.w(TAG, "Unknown message type: $messageType")
            }
        }
    }
    
    fun startAudioCapture() {
        audioCapture?.startCapture(serviceScope)
    }
    
    fun stopAudioCapture() {
        audioCapture?.stopCapture()
    }
    
    fun takePicture() {
        cameraCapture?.takePicture { imageData ->
            // Snapshot capturado, será enviado via DataChannel
            dataChannelManager?.sendSnapshot(imageData)
            Log.d(TAG, "Snapshot taken and sent: ${imageData.size} bytes")
        }
    }
    
    private fun sendAudioViaDataChannel(audioData: ByteArray) {
        dataChannelManager?.let { dcManager ->
            // Create audio message for Companion Desktop
            val audioMessage = JSONObject().apply {
                put("type", "audio_data")
                put("format", "pcm16")
                put("sampleRate", 16000)
                put("channels", 1)
                put("timestamp", System.currentTimeMillis())
                // Convert audio bytes to base64 for JSON transmission
                put("data", android.util.Base64.encodeToString(audioData, android.util.Base64.NO_WRAP))
            }
            
            dcManager.sendMessage(audioMessage.toString())
            Log.v(TAG, "Audio data sent via DataChannel: ${audioData.size} bytes")
        } ?: run {
            Log.w(TAG, "DataChannelManager not available, audio data dropped")
        }
    }
    
    private fun cleanup() {
        Log.d(TAG, "Cleaning up resources")
        
        audioCapture?.stopCapture()
        cameraCapture?.dispose()
        dataChannelManager?.dispose()
        signalingClient?.disconnect()
        currentPeerConnection?.close()
        hudOverlayManager?.hide()
        
        WebRTCManager.dispose()
        serviceScope.cancel()
        
        Log.d(TAG, "Cleanup completed")
    }
}