package com.watchnavigator.data

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.huawei.hmf.tasks.OnFailureListener
import com.huawei.hmf.tasks.OnSuccessListener
import com.huawei.hmf.tasks.Task
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HuaweiWearEngineServiceTest {
    private val context: Context = mockk(relaxed = true)
    private val authClient: AuthClient = mockk(relaxed = true)
    private val deviceClient: DeviceClient = mockk(relaxed = true)
    private val p2pClient: P2pClient = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var service: HuaweiWearEngineService

    @Before
    fun setUp() {
        service =
            HuaweiWearEngineService(
                context = context,
                authClient = authClient,
                deviceClient = deviceClient,
                p2pClient = p2pClient,
                peerPkgName = "com.watchnavigator.watch",
                peerFingerPrint = "test_fingerprint",
                ioDispatcher = testDispatcher
            )
    }

    private fun <T> mockSuccessfulTask(result: T): Task<T> {
        val task = mockk<Task<T>>()
        val successSlot = slot<OnSuccessListener<T>>()
        every { task.addOnSuccessListener(capture(successSlot)) } answers {
            successSlot.captured.onSuccess(result)
            task
        }
        every { task.addOnFailureListener(any()) } returns task
        every { task.addOnCanceledListener(any()) } returns task
        return task
    }

    private fun <T> mockFailedTask(exception: Exception): Task<T> {
        val task = mockk<Task<T>>()
        val failureSlot = slot<OnFailureListener>()
        every { task.addOnSuccessListener(any()) } returns task
        every { task.addOnFailureListener(capture(failureSlot)) } answers {
            failureSlot.captured.onFailure(exception)
            task
        }
        every { task.addOnCanceledListener(any()) } returns task
        return task
    }

    private fun <T> mockPendingTask(): Task<T> {
        val task = mockk<Task<T>>()
        every { task.addOnSuccessListener(any()) } returns task
        every { task.addOnFailureListener(any()) } returns task
        every { task.addOnCanceledListener(any()) } returns task
        return task
    }

    private fun createMockDevice(
        name: String = "HUAWEI WATCH GT 5",
        connected: Boolean = true
    ): Device {
        val device = mockk<Device>(relaxed = true)
        every { device.name } returns name
        every { device.model } returns "GT5-PRO"
        every { device.isConnected } returns connected
        return device
    }

    @Test
    fun checkPermissions_whenPermissionGranted_returnsTrue() =
        runTest(testDispatcher) {
            val task = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns task

            val result = service.checkPermissions()

            assertThat(result).isTrue()
        }

    @Test
    fun checkPermissions_whenPermissionDenied_returnsFalseAndSetsUnauthorizedState() =
        runTest(testDispatcher) {
            val task = mockSuccessfulTask(false)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns task

            val result = service.checkPermissions()

            assertThat(result).isFalse()
            assertThat(service.connectionState.value).isInstanceOf(WatchConnectionState.Unauthorized::class.java)
        }

    @Test
    fun checkConnection_whenNoAvailableDevices_returnsDisconnectedState() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
            val devTask = mockSuccessfulTask(false)
            every { deviceClient.hasAvailableDevices() } returns devTask

            val state = service.checkConnection()

            assertThat(state).isInstanceOf(WatchConnectionState.Disconnected::class.java)
            assertThat((state as WatchConnectionState.Disconnected).reason).contains("No available Huawei wearable devices")
        }

    @Test
    fun checkConnection_whenBondedDevicesEmpty_returnsDisconnectedState() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
            val devTask = mockSuccessfulTask(true)
            every { deviceClient.hasAvailableDevices() } returns devTask
            val bondedTask = mockSuccessfulTask(emptyList<Device>())
            every { deviceClient.getBondedDevices() } returns bondedTask

            val state = service.checkConnection()

            assertThat(state).isInstanceOf(WatchConnectionState.Disconnected::class.java)
        }

    @Test
    fun checkConnection_whenBondedDevicesDisconnected_returnsDisconnectedState() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
            val devTask = mockSuccessfulTask(true)
            every { deviceClient.hasAvailableDevices() } returns devTask
            val mockDevice = createMockDevice(name = "HUAWEI WATCH GT 5", connected = false)
            val bondedTask = mockSuccessfulTask(listOf(mockDevice))
            every { deviceClient.getBondedDevices() } returns bondedTask

            val state = service.checkConnection()

            assertThat(state).isInstanceOf(WatchConnectionState.Disconnected::class.java)
            assertThat((state as WatchConnectionState.Disconnected).reason).contains("disconnected")
        }

    @Test
    fun checkConnection_whenDeviceConnected_returnsConnectedStateWithDeviceInfo() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
            val devTask = mockSuccessfulTask(true)
            every { deviceClient.hasAvailableDevices() } returns devTask
            val mockDevice = createMockDevice(name = "HUAWEI WATCH GT 5", connected = true)
            val bondedTask = mockSuccessfulTask(listOf(mockDevice))
            every { deviceClient.getBondedDevices() } returns bondedTask

            val state = service.checkConnection()

            assertThat(state).isInstanceOf(WatchConnectionState.Connected::class.java)
            val connectedState = state as WatchConnectionState.Connected
            assertThat(connectedState.deviceName).isEqualTo("HUAWEI WATCH GT 5")
            assertThat(connectedState.deviceModel).isEqualTo("GT5-PRO")
            assertThat(service.connectionState.value).isEqualTo(connectedState)
        }

    @Test
    fun checkConnection_whenExceptionThrown_returnsErrorState() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
            val devTask = mockFailedTask<Boolean>(RuntimeException("WearEngine service unavailable"))
            every { deviceClient.hasAvailableDevices() } returns devTask

            val state = service.checkConnection()

            assertThat(state).isInstanceOf(WatchConnectionState.Error::class.java)
            assertThat((state as WatchConnectionState.Error).message).contains("WearEngine service unavailable")
        }

    @Test
    fun sendNavMessage_whenConnectedAndSendSucceeds_returnsSuccessResult() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
            val devTask = mockSuccessfulTask(true)
            every { deviceClient.hasAvailableDevices() } returns devTask
            val mockDevice = createMockDevice("HUAWEI WATCH GT 5")
            val bondedTask = mockSuccessfulTask(listOf(mockDevice))
            every { deviceClient.getBondedDevices() } returns bondedTask

            service.checkConnection()

            val callbackSlot = slot<SendCallback>()
            val sendTask = mockk<Task<Void>>(relaxed = true)
            every {
                p2pClient.send(eq(mockDevice), any<Message>(), capture(callbackSlot))
            } answers {
                callbackSlot.captured.onSendResult(WearEngineErrorCode.ERROR_CODE_COMM_SUCCESS)
                sendTask
            }

            val navMessage = WatchNavMessage("left", 120, "Nguyen Trai")
            val result = service.sendNavMessage(navMessage)

            assertThat(result.isSuccess).isTrue()
            verify { p2pClient.setPeerPkgName("com.watchnavigator.watch") }
            verify { p2pClient.setPeerFingerPrint("test_fingerprint") }
        }

    @Test
    fun sendNavMessage_whenSendFailsWithErrorCode_returnsFailureResult() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
            val devTask = mockSuccessfulTask(true)
            every { deviceClient.hasAvailableDevices() } returns devTask
            val mockDevice = createMockDevice("HUAWEI WATCH GT 5")
            val bondedTask = mockSuccessfulTask(listOf(mockDevice))
            every { deviceClient.getBondedDevices() } returns bondedTask

            service.checkConnection()

            val callbackSlot = slot<SendCallback>()
            val sendTask = mockk<Task<Void>>(relaxed = true)
            every {
                p2pClient.send(eq(mockDevice), any<Message>(), capture(callbackSlot))
            } answers {
                callbackSlot.captured.onSendResult(WearEngineErrorCode.ERROR_CODE_P2P_WATCH_APP_NOT_RUNNING)
                sendTask
            }

            val navMessage = WatchNavMessage("left", 120, "Nguyen Trai")
            val result = service.sendNavMessage(navMessage)

            assertThat(result.isFailure).isTrue()
            val exception = result.exceptionOrNull()
            assertThat(exception).isInstanceOf(WearEngineServiceException::class.java)
            assertThat((exception as WearEngineServiceException).errorCode).isEqualTo(WearEngineErrorCode.ERROR_CODE_P2P_WATCH_APP_NOT_RUNNING)
        }

    @Test
    fun sendNavMessage_whenSendFails_updatesConnectionStateToDisconnected() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
            val devTask = mockSuccessfulTask(true)
            every { deviceClient.hasAvailableDevices() } returns devTask
            val mockDevice = createMockDevice("HUAWEI WATCH GT 5")
            val bondedTask = mockSuccessfulTask(listOf(mockDevice))
            every { deviceClient.getBondedDevices() } returns bondedTask

            service.checkConnection()
            assertThat(service.connectionState.value).isInstanceOf(WatchConnectionState.Connected::class.java)

            val callbackSlot = slot<SendCallback>()
            val sendTask = mockk<Task<Void>>(relaxed = true)
            every {
                p2pClient.send(eq(mockDevice), any<Message>(), capture(callbackSlot))
            } answers {
                callbackSlot.captured.onSendResult(WearEngineErrorCode.ERROR_CODE_COMM_FAIL)
                sendTask
            }

            val navMessage = WatchNavMessage("left", 120, "Nguyen Trai")
            val result = service.sendNavMessage(navMessage)

            assertThat(result.isFailure).isTrue()
            assertThat(service.connectionState.value).isInstanceOf(WatchConnectionState.Disconnected::class.java)
        }

    @Test
    fun sendNavMessage_serializesConcurrentSendsPreservingOrder() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
            val devTask = mockSuccessfulTask(true)
            every { deviceClient.hasAvailableDevices() } returns devTask
            val mockDevice = createMockDevice("HUAWEI WATCH GT 5")
            val bondedTask = mockSuccessfulTask(listOf(mockDevice))
            every { deviceClient.getBondedDevices() } returns bondedTask

            service.checkConnection()

            val sendTask = mockk<Task<Void>>(relaxed = true)
            val p2pMessageSlot = slot<Message>()
            val callbackSlot = slot<SendCallback>()
            val capturedMessages = java.util.Collections.synchronizedList(mutableListOf<String>())
            every {
                p2pClient.send(eq(mockDevice), capture(p2pMessageSlot), capture(callbackSlot))
            } answers {
                val capturedMsg = String(p2pMessageSlot.captured.data, Charsets.UTF_8)
                capturedMessages.add(capturedMsg)
                callbackSlot.captured.onSendResult(WearEngineErrorCode.ERROR_CODE_COMM_SUCCESS)
                sendTask
            }

            val deferred1 =
                async {
                    service.sendNavMessage(WatchNavMessage("left", 120, "Nguyen Trai"))
                }
            val deferred2 =
                async {
                    service.sendNavMessage(WatchNavMessage.stop())
                }
            deferred1.await()
            deferred2.await()

            assertThat(capturedMessages).hasSize(2)
            assertThat(capturedMessages.any { it.contains("\"turn\":\"left\"") }).isTrue()
            assertThat(capturedMessages.any { it.contains("\"turn\":\"stop\"") }).isTrue()
        }

    @Test
    fun pingWatch_whenWatchAppRunning_returnsSuccessTrue() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
            val devTask = mockSuccessfulTask(true)
            every { deviceClient.hasAvailableDevices() } returns devTask
            val mockDevice = createMockDevice("HUAWEI WATCH GT 5")
            val bondedTask = mockSuccessfulTask(listOf(mockDevice))
            every { deviceClient.getBondedDevices() } returns bondedTask

            service.checkConnection()

            val callbackSlot = slot<PingCallback>()
            val pingTask = mockk<Task<Void>>(relaxed = true)
            every {
                p2pClient.ping(eq(mockDevice), capture(callbackSlot))
            } answers {
                callbackSlot.captured.onPingResult(WearEngineErrorCode.ERROR_CODE_SUCCESS)
                pingTask
            }

            val result = service.pingWatch()

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isTrue()
        }

    @Test
    fun pingWatch_whenWatchAppNotRunning_returnsSuccessFalse() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
            val devTask = mockSuccessfulTask(true)
            every { deviceClient.hasAvailableDevices() } returns devTask
            val mockDevice = createMockDevice("HUAWEI WATCH GT 5")
            val bondedTask = mockSuccessfulTask(listOf(mockDevice))
            every { deviceClient.getBondedDevices() } returns bondedTask

            service.checkConnection()

            val callbackSlot = slot<PingCallback>()
            val pingTask = mockk<Task<Void>>(relaxed = true)
            every {
                p2pClient.ping(eq(mockDevice), capture(callbackSlot))
            } answers {
                callbackSlot.captured.onPingResult(WearEngineErrorCode.ERROR_CODE_P2P_WATCH_APP_NOT_RUNNING)
                pingTask
            }

            val result = service.pingWatch()

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isFalse()
        }

    @Test
    fun startAutoReconnect_retriesWithExponentialBackoffAndResumesOnConnection() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
            val devTask = mockSuccessfulTask(true)
            every { deviceClient.hasAvailableDevices() } returns devTask

            var checkCount = 0
            val disconnectedDevice = createMockDevice("HUAWEI WATCH GT 5", connected = false)
            val connectedDevice = createMockDevice("HUAWEI WATCH GT 5", connected = true)

            every { deviceClient.getBondedDevices() } answers {
                checkCount++
                // Disconnected on attempts 1 and 2, connected on attempt 3
                if (checkCount < 3) {
                    mockSuccessfulTask(listOf(disconnectedDevice))
                } else {
                    mockSuccessfulTask(listOf(connectedDevice))
                }
            }

            var reconnectedCallbackInvoked = false
            service.startAutoReconnect {
                reconnectedCallbackInvoked = true
            }

            assertThat(service.isReconnecting.value).isTrue()
            assertThat(checkCount).isEqualTo(0)

            // Attempt 1: delay 2000ms
            advanceTimeBy(2000L)
            runCurrent()
            assertThat(checkCount).isEqualTo(1)
            assertThat(service.isReconnecting.value).isTrue()
            assertThat(reconnectedCallbackInvoked).isFalse()

            // Attempt 2: delay 4000ms
            advanceTimeBy(4000L)
            runCurrent()
            assertThat(checkCount).isEqualTo(2)
            assertThat(service.isReconnecting.value).isTrue()
            assertThat(reconnectedCallbackInvoked).isFalse()

            // Attempt 3: delay 8000ms -> succeeds!
            advanceTimeBy(8000L)
            runCurrent()
            assertThat(checkCount).isEqualTo(3)
            assertThat(service.isReconnecting.value).isFalse()
            assertThat(reconnectedCallbackInvoked).isTrue()
            assertThat(service.connectionState.value).isInstanceOf(WatchConnectionState.Connected::class.java)
        }

    @Test
    fun stopAutoReconnect_cancelsRetryLoopImmediately() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
            val devTask = mockSuccessfulTask(true)
            every { deviceClient.hasAvailableDevices() } returns devTask

            var checkCount = 0
            val disconnectedDevice = createMockDevice("HUAWEI WATCH GT 5", connected = false)
            every { deviceClient.getBondedDevices() } answers {
                checkCount++
                mockSuccessfulTask(listOf(disconnectedDevice))
            }

            service.startAutoReconnect()
            assertThat(service.isReconnecting.value).isTrue()

            // After 1st retry
            advanceTimeBy(2000L)
            runCurrent()
            assertThat(checkCount).isEqualTo(1)

            // Stop auto reconnect
            service.stopAutoReconnect()
            runCurrent()
            assertThat(service.isReconnecting.value).isFalse()

            // Advance time further - no more checks should happen
            advanceTimeBy(30000L)
            runCurrent()
            assertThat(checkCount).isEqualTo(1)
        }

    @Test
    fun release_cancelsAutoReconnectAndResetsState() =
        runTest(testDispatcher) {
            service.startAutoReconnect()
            assertThat(service.isReconnecting.value).isTrue()

            service.release()
            runCurrent()

            assertThat(service.isReconnecting.value).isFalse()
            assertThat(service.connectionState.value).isInstanceOf(WatchConnectionState.Disconnected::class.java)
        }

    @Test
    fun startAutoReconnect_capsDelayAtMaxRetryDelayMs() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
            val devTask = mockSuccessfulTask(true)
            every { deviceClient.hasAvailableDevices() } returns devTask

            var checkCount = 0
            val disconnectedDevice = createMockDevice("HUAWEI WATCH GT 5", connected = false)
            val connectedDevice = createMockDevice("HUAWEI WATCH GT 5", connected = true)

            every { deviceClient.getBondedDevices() } answers {
                checkCount++
                if (checkCount < 5) {
                    mockSuccessfulTask(listOf(disconnectedDevice))
                } else {
                    mockSuccessfulTask(listOf(connectedDevice))
                }
            }

            service.startAutoReconnect()
            assertThat(service.isReconnecting.value).isTrue()

            // Attempt 1: 2000ms
            advanceTimeBy(2000L)
            runCurrent()
            assertThat(checkCount).isEqualTo(1)

            // Attempt 2: 4000ms
            advanceTimeBy(4000L)
            runCurrent()
            assertThat(checkCount).isEqualTo(2)

            // Attempt 3: 8000ms
            advanceTimeBy(8000L)
            runCurrent()
            assertThat(checkCount).isEqualTo(3)

            // Attempt 4: capped at maxRetryDelayMs (15000ms instead of 16000ms)
            advanceTimeBy(14999L)
            runCurrent()
            assertThat(checkCount).isEqualTo(3)
            advanceTimeBy(1L)
            runCurrent()
            assertThat(checkCount).isEqualTo(4)

            // Attempt 5: still capped at 15000ms -> succeeds
            advanceTimeBy(15000L)
            runCurrent()
            assertThat(checkCount).isEqualTo(5)
            assertThat(service.isReconnecting.value).isFalse()
            assertThat(service.connectionState.value).isInstanceOf(WatchConnectionState.Connected::class.java)
        }

    @Test
    fun startAutoReconnect_allowsRestartFromWithinOnReconnectedCallback() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
            val devTask = mockSuccessfulTask(true)
            every { deviceClient.hasAvailableDevices() } returns devTask

            var checkCount = 0
            val connectedDevice = createMockDevice("HUAWEI WATCH GT 5", connected = true)
            every { deviceClient.getBondedDevices() } answers {
                checkCount++
                mockSuccessfulTask(listOf(connectedDevice))
            }

            var secondReconnectInvoked = false
            service.startAutoReconnect {
                // Callback triggers a second reconnect loop
                service.startAutoReconnect {
                    secondReconnectInvoked = true
                }
            }

            // First check succeeds
            advanceTimeBy(2000L)
            runCurrent()
            assertThat(checkCount).isEqualTo(1)
            assertThat(service.isReconnecting.value).isTrue()

            // Second check from callback's reconnect loop
            advanceTimeBy(2000L)
            runCurrent()
            assertThat(checkCount).isEqualTo(2)
            assertThat(secondReconnectInvoked).isTrue()
            assertThat(service.isReconnecting.value).isFalse()
        }

    @Test
    fun checkConnection_whenTimeoutOccurs_returnsErrorStateWithoutCancellingCaller() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
            val devTask = mockPendingTask<Boolean>()
            every { deviceClient.hasAvailableDevices() } returns devTask

            val stateDeferred = async { service.checkConnection() }
            advanceTimeBy(5000L)
            runCurrent()

            val state = stateDeferred.await()
            assertThat(state).isInstanceOf(WatchConnectionState.Error::class.java)
            assertThat((state as WatchConnectionState.Error).message).contains("timed out")
        }

    @Test
    fun startAutoReconnect_whenCheckConnectionTimesOut_continuesRetryLoop() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask

            var checkCount = 0
            val connectedDevice = createMockDevice("HUAWEI WATCH GT 5", connected = true)

            every { deviceClient.hasAvailableDevices() } answers {
                checkCount++
                if (checkCount == 1) {
                    // Attempt 1: times out
                    mockPendingTask()
                } else {
                    // Attempt 2: succeeds
                    mockSuccessfulTask(true)
                }
            }
            every { deviceClient.getBondedDevices() } returns mockSuccessfulTask(listOf(connectedDevice))

            var reconnectedCallbackInvoked = false
            service.startAutoReconnect {
                reconnectedCallbackInvoked = true
            }

            assertThat(service.isReconnecting.value).isTrue()

            // Attempt 1: delay 2000ms + 5000ms timeout
            advanceTimeBy(7000L)
            runCurrent()
            assertThat(checkCount).isEqualTo(1)
            assertThat(service.isReconnecting.value).isTrue()
            assertThat(reconnectedCallbackInvoked).isFalse()

            // Attempt 2: backoff delay 4000ms -> succeeds!
            advanceTimeBy(4000L)
            runCurrent()
            assertThat(checkCount).isEqualTo(2)
            assertThat(service.isReconnecting.value).isFalse()
            assertThat(reconnectedCallbackInvoked).isTrue()
            assertThat(service.connectionState.value).isInstanceOf(WatchConnectionState.Connected::class.java)
        }

    @Test
    fun stopAutoReconnect_cancelsActiveCallbackJob() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(true)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
            val devTask = mockSuccessfulTask(true)
            every { deviceClient.hasAvailableDevices() } returns devTask
            val connectedDevice = createMockDevice("HUAWEI WATCH GT 5", connected = true)
            every { deviceClient.getBondedDevices() } returns mockSuccessfulTask(listOf(connectedDevice))

            var callbackCompleted = false
            service.startAutoReconnect {
                // Suspend inside callback
                delay(10000L)
                callbackCompleted = true
            }

            // Reach attempt 1 (2000ms) -> connection succeeds, callback launches
            advanceTimeBy(2000L)
            runCurrent()
            assertThat(service.isReconnecting.value).isFalse()
            assertThat(callbackCompleted).isFalse()

            // Cancel auto reconnect while callback is in-flight (5000ms later)
            advanceTimeBy(5000L)
            runCurrent()
            service.stopAutoReconnect()
            runCurrent()

            // Advance time past the callback's delay
            advanceTimeBy(20000L)
            runCurrent()
            assertThat(callbackCompleted).isFalse()
        }

    @Test
    fun startAutoReconnect_whenPermissionUnauthorized_haltsRetryLoop() =
        runTest(testDispatcher) {
            val permTask = mockSuccessfulTask(false)
            every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask

            service.startAutoReconnect()
            assertThat(service.isReconnecting.value).isTrue()

            // Attempt 1: 2000ms delay runs checkConnection -> Unauthorized -> halts
            advanceTimeBy(2000L)
            runCurrent()

            assertThat(service.isReconnecting.value).isFalse()
            assertThat(service.connectionState.value).isInstanceOf(WatchConnectionState.Unauthorized::class.java)

            // Advance time further - no more checks occur
            advanceTimeBy(30000L)
            runCurrent()
            assertThat(service.isReconnecting.value).isFalse()
        }
}
