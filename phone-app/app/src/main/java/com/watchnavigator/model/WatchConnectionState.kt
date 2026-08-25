package com.watchnavigator.model

sealed class WatchConnectionState {
    data class Disconnected(
        val reason: String? = null
    ) : WatchConnectionState()

    object Connecting : WatchConnectionState()

    data class Connected(
        val deviceName: String,
        val deviceModel: String? = null
    ) : WatchConnectionState()

    data class Unauthorized(
        val message: String = "Wear Engine permission required"
    ) : WatchConnectionState()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : WatchConnectionState()
}
