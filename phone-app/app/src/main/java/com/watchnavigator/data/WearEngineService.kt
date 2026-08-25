package com.watchnavigator.data

import android.content.Context
import com.huawei.hmf.tasks.Task
import com.huawei.wearengine.HiWear
import com.huawei.wearengine.auth.AuthClient
import com.huawei.wearengine.auth.AuthCallback
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WearEngineServiceException(
    val errorCode: Int,
    message: String
) : Exception("WearEngine error $errorCode: $message")

private const val DEFAULT_TIMEOUT_MS = 5000L

interface WearEngineService {
    val connectionState: StateFlow<WatchConnectionState>
    val isReconnecting: StateFlow<Boolean>
    suspend fun checkPermissions(): Boolean
    suspend fun requestPermission(): Boolean
    suspend fun checkConnection(): WatchConnectionState
    suspend fun sendNavMessage(message: WatchNavMessage): Result<Unit>
    suspend fun pingWatch(): Result<Boolean>
    fun startAutoReconnect(onReconnected: (suspend () -> Unit)? = null)
    fun stopAutoReconnect()
    fun release()
}

class HuaweiWearEngineService(
    private val context: Context,
    private val authClient: AuthClient = HiWear.getAuthClient(context),
    private val deviceClient: DeviceClient = HiWear.getDeviceClient(context),
    private val p2pClient: P2pClient = HiWear.getP2pClient(context),
    private val peerPkgName: String = DEFAULT_PEER_PKG_NAME,
    private val peerFingerPrint: String = DEFAULT_PEER_FINGERPRINT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : WearEngineService {

    companion object {
        const val DEFAULT_PEER_PKG_NAME = "com.watchnavigator.watch"
        const val DEFAULT_PEER_FINGERPRINT = "com.watchnavigator.watch_B/gY2TR32LwRrqp46Ucfk+p49a6i3aB+y2Xw3gU5n5U="
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val DEFAULT_INITIAL_RETRY_DELAY_MS = 2000L
        const val DEFAULT_MAX_RETRY_DELAY_MS = 15000L
        const val DEFAULT_BACKOFF_MULTIPLIER = 2.0
    }

    private val _connectionState = MutableStateFlow<WatchConnectionState>(WatchConnectionState.Disconnected())
    override val connectionState: StateFlow<WatchConnectionState> = _connectionState.asStateFlow()

    private val _isReconnecting = MutableStateFlow(false)
    override val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()

    private val serviceScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    var initialRetryDelayMs: Long = DEFAULT_INITIAL_RETRY_DELAY_MS
    var maxRetryDelayMs: Long = DEFAULT_MAX_RETRY_DELAY_MS
    var backoffMultiplier: Double = DEFAULT_BACKOFF_MULTIPLIER

    private val reconnectLock = Any()
    private var reconnectJob: Job? = null
    private var callbackJob: Job? = null
    private var reconnectCallback: (suspend () -> Unit)? = null

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

    override suspend fun checkPermissions(): Boolean = withContext(ioDispatcher) {
        try {
            val hasPermission = authClient.checkPermission(Permission.DEVICE_MANAGER).awaitResult()
            if (!hasPermission) {
                _connectionState.value = WatchConnectionState.Unauthorized(
                    "Huawei Wear Engine Device Manager permission not granted"
                )
            }
            hasPermission
        } catch (e: TimeoutCancellationException) {
            _connectionState.value = WatchConnectionState.Unauthorized(
                "Permission check timed out: ${e.message}"
            )
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _connectionState.value = WatchConnectionState.Unauthorized(
                "Failed to verify Wear Engine permissions: ${e.message}"
            )
            false
        }
    }

    override suspend fun requestPermission(): Boolean = withContext(ioDispatcher) {
        try {
            val granted = suspendCancellableCoroutine<Boolean> { continuation ->
                val authCallback = object : AuthCallback {
                    override fun onOk(permissions: Array<out Permission>?) {
                        val hasDeviceManager = permissions?.any {
                            it == Permission.DEVICE_MANAGER || it.name == Permission.DEVICE_MANAGER.name
                        } ?: false
                        if (continuation.isActive) {
                            continuation.resume(hasDeviceManager)
                        }
                    }

                    override fun onCancel() {
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                }

                try {
                    val task = authClient.requestPermission(authCallback, Permission.DEVICE_MANAGER)
                    task.addOnFailureListener { exception ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(exception)
                        }
                    }
                    task.addOnCanceledListener {
                        if (continuation.isActive) {
                            continuation.cancel()
                        }
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            }

            if (granted) {
                val state = checkConnection()
                state is WatchConnectionState.Connected || state is WatchConnectionState.Disconnected
            } else {
                _connectionState.value = WatchConnectionState.Unauthorized(
                    "Huawei Wear Engine permission request was cancelled. Please grant Device Manager permission to connect to your watch."
                )
                false
            }
        } catch (e: TimeoutCancellationException) {
            _connectionState.value = WatchConnectionState.Unauthorized(
                "Permission request timed out: ${e.message}"
            )
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _connectionState.value = WatchConnectionState.Unauthorized(
                "Failed to request Wear Engine permissions: ${e.message}"
            )
            false
        }
    }

    override suspend fun checkConnection(): WatchConnectionState = withContext(ioDispatcher) {
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
        } catch (e: TimeoutCancellationException) {
            val state = WatchConnectionState.Error(
                message = "Connection check timed out: ${e.message}",
                cause = e
            )
            _connectionState.value = state
            activeDevice = null
            state
        } catch (e: CancellationException) {
            throw e
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

    override suspend fun sendNavMessage(message: WatchNavMessage): Result<Unit> = withContext(ioDispatcher) {
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
                    _connectionState.value = WatchConnectionState.Disconnected(
                        "WearEngine send failed: $errorMsg"
                    )
                    Result.failure(WearEngineServiceException(resultCode, errorMsg))
                }
            } catch (e: TimeoutCancellationException) {
                activeDevice = null
                _connectionState.value = WatchConnectionState.Disconnected(
                    "WearEngine send timed out: ${e.message}"
                )
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                activeDevice = null
                _connectionState.value = WatchConnectionState.Disconnected(
                    "WearEngine communication error: ${e.message}"
                )
                Result.failure(e)
            }
        }
    }

    override suspend fun pingWatch(): Result<Boolean> = withContext(ioDispatcher) {
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
        } catch (e: TimeoutCancellationException) {
            activeDevice = null
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            activeDevice = null
            Result.failure(e)
        }
    }

    override fun startAutoReconnect(onReconnected: (suspend () -> Unit)?) {
        synchronized(reconnectLock) {
            if (onReconnected != null) {
                this.reconnectCallback = onReconnected
            }
            if (reconnectJob?.isActive == true) {
                return
            }

            _isReconnecting.value = true
            reconnectJob = serviceScope.launch {
                var currentDelayMs = initialRetryDelayMs
                try {
                    while (isActive && _isReconnecting.value) {
                        delay(currentDelayMs)
                        val state = checkConnection()
                        if (state is WatchConnectionState.Unauthorized) {
                            synchronized(reconnectLock) {
                                reconnectJob = null
                                _isReconnecting.value = false
                            }
                            break
                        }
                        if (state is WatchConnectionState.Connected) {
                            val callback: (suspend () -> Unit)?
                            synchronized(reconnectLock) {
                                reconnectJob = null
                                _isReconnecting.value = false
                                callback = reconnectCallback
                                if (callback != null) {
                                    callbackJob = serviceScope.launch {
                                        try {
                                            callback.invoke()
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            // Ignore callback exceptions to prevent crashing serviceScope
                                        } finally {
                                            synchronized(reconnectLock) {
                                                if (callbackJob == coroutineContext[Job]) {
                                                    callbackJob = null
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            break
                        }
                        currentDelayMs = (currentDelayMs * backoffMultiplier)
                            .toLong()
                            .coerceAtMost(maxRetryDelayMs)
                    }
                } finally {
                    synchronized(reconnectLock) {
                        if (reconnectJob == coroutineContext[Job]) {
                            reconnectJob = null
                            _isReconnecting.value = false
                        }
                    }
                }
            }
        }
    }

    override fun stopAutoReconnect() {
        synchronized(reconnectLock) {
            reconnectJob?.cancel()
            reconnectJob = null
            callbackJob?.cancel()
            callbackJob = null
            _isReconnecting.value = false
            reconnectCallback = null
        }
    }
    override fun release() {
        stopAutoReconnect()
        serviceScope.coroutineContext.cancelChildren()
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
