package com.posecoach.ui.activities

/**
 * Connection status for Gemini Live API
 */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RESUMING,
    ERROR
}