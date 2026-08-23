package com.watchnavigator.data

import android.content.Context
import com.huawei.hmf.tasks.Task
import com.huawei.wearengine.HiWear
import com.huawei.wearengine.auth.AuthClient
import com.huawei.wearengine.auth.Permission
import com.huawei.wearengine.common.WearEngineErrorCode
import com.huawei.wearengine.device.Device
import com.huawei.wearengine.device.DeviceClient
import com.huawei.wearengine.p2p.Message
import com.huawei.wearengine.p2p.P2pClient
import com.huawei.wearengine.p2p.PingCallback
import com.huawei.wearengine.p2p.SendCallback
import com.watchnavigator.model.WatchConnectionState
import com.watchnavigator.model.WatchNavMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WearEngineServiceException(
    val errorCode: Int,
    message: String
) : Exception("WearEngine error $errorCode: $message")

private const val DEFAULT_TIMEOUT_MS = 5000L

interface WearEngineService {
    val connectionState: StateFlow<WatchConnectionState>
    suspend fun checkPermissions(): Boolean
    suspend fun checkConnection(): WatchConnectionState
    suspend fun sendNavMessage(message: WatchNavMessage): Result<Unit>
    suspend fun pingWatch(): Result<Boolean>
    fun release()
}

class HuaweiWearEngineService(
    private val context: Context,
    private val authClient: AuthClient = HiWear.getAuthClient(context),
    private val deviceClient: DeviceClient = HiWear.getDeviceClient(context),
    private val p2pClient: P2pClient = HiWear.getP2pClient(context),
    private val peerPkgName: String = DEFAULT_PEER_PKG_NAME,
    private val peerFingerPrint: String = DEFAULT_PEER_FINGERPRINT
) : WearEngineService {

    companion object {
        const val DEFAULT_PEER_PKG_NAME = "com.watchnavigator.watch"
        const val DEFAULT_PEER_FINGERPRINT = "com.watchnavigator.watch_B/gY2TR32LwRrqp46Ucfk+p49a6i3aB+y2Xw3gU5n5U="
        const val DEFAULT_TIMEOUT_MS = 5000L
    }

    private val _connectionState = MutableStateFlow<WatchConnectionState>(WatchConnectionState.Disconnected())
    override val connectionState: StateFlow<WatchConnectionState> = _connectionState.asStateFlow()

    private val sendMutex = Mutex()

    @Volatile
    private var activeDevice: Device? = null

    init {
        try {
            p2pClient.setPeerPkgName(peerPkgName)
            p2pClient.setPeerFingerPrint(peerFingerPrint)
        } catch (e: Exception) {
            // Ignored during testing or when client is not yet bound
        }
    }

    override suspend fun checkPermissions(): Boolean = withContext(Dispatchers.IO) {
        try {
            val hasPermission = authClient.checkPermission(Permission.DEVICE_MANAGER).awaitResult()
            if (!hasPermission) {
                _connectionState.value = WatchConnectionState.Unauthorized(
                    "Huawei Wear Engine Device Manager permission not granted"
                )
            }
            hasPermission
        } catch (e: Exception) {
            _connectionState.value = WatchConnectionState.Unauthorized(
                "Failed to verify Wear Engine permissions: ${e.message}"
            )
            false
        }
    }

    override suspend fun checkConnection(): WatchConnectionState = withContext(Dispatchers.IO) {
        _connectionState.value = WatchConnectionState.Connecting
        try {
            val isAuthorized = checkPermissions()
            if (!isAuthorized) {
                return@withContext _connectionState.value
            }

            val hasDevices = deviceClient.hasAvailableDevices().awaitResult()
            if (!hasDevices) {
                val state = WatchConnectionState.Disconnected("No available Huawei wearable devices found")
                _connectionState.value = state
                activeDevice = null
                return@withContext state
            }

            val bondedDevices = deviceClient.getBondedDevices().awaitResult()
            if (bondedDevices.isNullOrEmpty()) {
                val state = WatchConnectionState.Disconnected("No paired Huawei watches found in Huawei Health")
                _connectionState.value = state
                activeDevice = null
                return@withContext state
            }

            val connectedDevice = bondedDevices.firstOrNull { it.isConnected }
            if (connectedDevice == null) {
                val firstPaired = bondedDevices.first()
                val state = WatchConnectionState.Disconnected("Watch '${firstPaired.name ?: "Huawei Watch"}' is paired but disconnected")
                _connectionState.value = state
                activeDevice = null
                return@withContext state
            }

            activeDevice = connectedDevice
            val state = WatchConnectionState.Connected(
                deviceName = connectedDevice.name ?: "Huawei Watch",
                deviceModel = connectedDevice.model
            )
            _connectionState.value = state
            state
        } catch (e: Exception) {
            val state = WatchConnectionState.Error(
                message = e.message ?: "Failed to connect to Huawei Wear Engine",
                cause = e
            )
            _connectionState.value = state
            activeDevice = null
            state
        }
    }

    override suspend fun sendNavMessage(message: WatchNavMessage): Result<Unit> = withContext(Dispatchers.IO) {
        sendMutex.withLock {
            try {
                var target = activeDevice
                if (target == null) {
                    val state = checkConnection()
                    if (state !is WatchConnectionState.Connected) {
                        return@withLock Result.failure(
                            IllegalStateException("Cannot send navigation message: watch not connected")
                        )
                    }
                    target = activeDevice ?: return@withLock Result.failure(
                        IllegalStateException("No active watch device available")
                    )
                }

                try {
                    p2pClient.setPeerPkgName(peerPkgName)
                    p2pClient.setPeerFingerPrint(peerFingerPrint)
                } catch (_: Exception) {}

                val jsonBytes = message.toJsonString().toByteArray(Charsets.UTF_8)
                val p2pMessage = Message.Builder()
                    .setPayload(jsonBytes)
                    .setDescription("Nav instruction")
                    .build()

                val resultCode = p2pClient.sendAsync(target, p2pMessage)
                if (resultCode == WearEngineErrorCode.ERROR_CODE_SUCCESS ||
                    resultCode == WearEngineErrorCode.ERROR_CODE_COMM_SUCCESS
                ) {
                    Result.success(Unit)
                } else {
                    val errorMsg = WearEngineErrorCode.getErrorMsgFromCode(resultCode) ?: "Result code $resultCode"
                    activeDevice = null
                    Result.failure(WearEngineServiceException(resultCode, errorMsg))
                }
            } catch (e: Exception) {
                activeDevice = null
                Result.failure(e)
            }
        }
    }

    override suspend fun pingWatch(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            var target = activeDevice
            if (target == null) {
                val state = checkConnection()
                if (state !is WatchConnectionState.Connected) {
                    return@withContext Result.success(false)
                }
                target = activeDevice ?: return@withContext Result.success(false)
            }

            val resultCode = p2pClient.pingAsync(target)
            if (resultCode == WearEngineErrorCode.ERROR_CODE_SUCCESS ||
                resultCode == WearEngineErrorCode.ERROR_CODE_P2P_WATCH_APP_RUNNING
            ) {
                Result.success(true)
            } else {
                activeDevice = null
                Result.success(false)
            }
        } catch (e: Exception) {
            activeDevice = null
            Result.failure(e)
        }
    }

    override fun release() {
        activeDevice = null
        _connectionState.value = WatchConnectionState.Disconnected()
    }
}

suspend fun <T> Task<T>.awaitResult(timeoutMillis: Long = DEFAULT_TIMEOUT_MS): T =
    kotlinx.coroutines.withTimeout(timeoutMillis) {
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            addOnFailureListener { exception ->
                if (continuation.isActive) {
                    continuation.resumeWithException(exception)
                }
            }
            addOnCanceledListener {
                if (continuation.isActive) {
                    continuation.cancel()
                }
            }
        }
    }

private suspend fun P2pClient.sendAsync(
    device: Device,
    message: Message,
    timeoutMillis: Long = DEFAULT_TIMEOUT_MS
): Int = kotlinx.coroutines.withTimeout(timeoutMillis) {
    suspendCancellableCoroutine { continuation ->
        val sendCallback = object : SendCallback {
            override fun onSendResult(resultCode: Int) {
                if (continuation.isActive) {
                    continuation.resume(resultCode)
                }
            }

            override fun onSendProgress(progress: Long) {
                // Optional progress notification
            }
        }
        try {
            send(device, message, sendCallback)
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }
    }
}

private suspend fun P2pClient.pingAsync(
    device: Device,
    timeoutMillis: Long = DEFAULT_TIMEOUT_MS
): Int = kotlinx.coroutines.withTimeout(timeoutMillis) {
    suspendCancellableCoroutine { continuation ->
        val pingCallback = PingCallback { resultCode ->
            if (continuation.isActive) {
                continuation.resume(resultCode)
            }
        }
        try {
            ping(device, pingCallback)
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }
    }
}
