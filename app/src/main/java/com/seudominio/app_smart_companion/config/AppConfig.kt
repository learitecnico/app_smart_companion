package com.seudominio.app_smart_companion.config

object AppConfig {
    // Companion Desktop server configuration
    // TODO: In production, this should be configurable via settings
    const val COMPANION_HOST = "192.168.1.7" // Replace with your PC's IP
    const val COMPANION_PORT = 3000
    
    val SIGNALING_URL: String
        get() = "ws://$COMPANION_HOST:$COMPANION_PORT/signaling"
    
    // WebRTC Configuration
    val ICE_SERVERS = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302"
    )
    
    // Audio Configuration
    const val AUDIO_SAMPLE_RATE = 16000
    const val AUDIO_CHANNELS = 1 // Mono
    const val AUDIO_ENCODING_BIT_RATE = 32000
    
    // Camera Configuration
    const val MAX_IMAGE_SIZE_KB = 200
    const val JPEG_QUALITY = 80
    
    // Network timeouts
    const val CONNECTION_TIMEOUT_MS = 10000L
    const val RECONNECT_DELAY_MS = 5000L
    const val MAX_RECONNECT_ATTEMPTS = 5
}