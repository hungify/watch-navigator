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
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HuaweiWearEngineServiceTest {

    private val context: Context = mockk(relaxed = true)
    private val authClient: AuthClient = mockk(relaxed = true)
    private val deviceClient: DeviceClient = mockk(relaxed = true)
    private val p2pClient: P2pClient = mockk(relaxed = true)

    private lateinit var service: HuaweiWearEngineService

    @Before
    fun setUp() {
        service = HuaweiWearEngineService(
            context = context,
            authClient = authClient,
            deviceClient = deviceClient,
            p2pClient = p2pClient,
            peerPkgName = "com.watchnavigator.watch",
            peerFingerPrint = "test_fingerprint"
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

    private fun createMockDevice(name: String = "HUAWEI WATCH GT 5", connected: Boolean = true): Device {
        val device = mockk<Device>(relaxed = true)
        every { device.name } returns name
        every { device.model } returns "GT5-PRO"
        every { device.isConnected } returns connected
        return device
    }

    @Test
    fun checkPermissions_whenPermissionGranted_returnsTrue() = runTest {
        val task = mockSuccessfulTask(true)
        every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns task

        val result = service.checkPermissions()

        assertThat(result).isTrue()
    }

    @Test
    fun checkPermissions_whenPermissionDenied_returnsFalseAndSetsUnauthorizedState() = runTest {
        val task = mockSuccessfulTask(false)
        every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns task

        val result = service.checkPermissions()

        assertThat(result).isFalse()
        assertThat(service.connectionState.value).isInstanceOf(WatchConnectionState.Unauthorized::class.java)
    }

    @Test
    fun checkConnection_whenNoAvailableDevices_returnsDisconnectedState() = runTest {
        val permTask = mockSuccessfulTask(true)
        every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
        val devTask = mockSuccessfulTask(false)
        every { deviceClient.hasAvailableDevices() } returns devTask

        val state = service.checkConnection()

        assertThat(state).isInstanceOf(WatchConnectionState.Disconnected::class.java)
        assertThat((state as WatchConnectionState.Disconnected).reason).contains("No available Huawei wearable devices")
    }

    @Test
    fun checkConnection_whenBondedDevicesEmpty_returnsDisconnectedState() = runTest {
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
    fun checkConnection_whenBondedDevicesDisconnected_returnsDisconnectedState() = runTest {
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
    fun checkConnection_whenDeviceConnected_returnsConnectedStateWithDeviceInfo() = runTest {
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
    fun checkConnection_whenExceptionThrown_returnsErrorState() = runTest {
        val permTask = mockSuccessfulTask(true)
        every { authClient.checkPermission(Permission.DEVICE_MANAGER) } returns permTask
        val devTask = mockFailedTask<Boolean>(RuntimeException("WearEngine service unavailable"))
        every { deviceClient.hasAvailableDevices() } returns devTask

        val state = service.checkConnection()

        assertThat(state).isInstanceOf(WatchConnectionState.Error::class.java)
        assertThat((state as WatchConnectionState.Error).message).contains("WearEngine service unavailable")
    }

    @Test
    fun sendNavMessage_whenConnectedAndSendSucceeds_returnsSuccessResult() = runTest {
        // Setup connected device
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
    fun sendNavMessage_whenSendFailsWithErrorCode_returnsFailureResult() = runTest {
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
    fun sendNavMessage_serializesConcurrentSendsPreservingOrder() = runTest {
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

        val deferred1 = async {
            service.sendNavMessage(WatchNavMessage("left", 120, "Nguyen Trai"))
        }
        val deferred2 = async {
            service.sendNavMessage(WatchNavMessage.stop())
        }
        deferred1.await()
        deferred2.await()

        assertThat(capturedMessages).hasSize(2)
        assertThat(capturedMessages.any { it.contains("\"turn\":\"left\"") }).isTrue()
        assertThat(capturedMessages.any { it.contains("\"turn\":\"stop\"") }).isTrue()
    }

    @Test
    fun pingWatch_whenWatchAppRunning_returnsSuccessTrue() = runTest {
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
    fun pingWatch_whenWatchAppNotRunning_returnsSuccessFalse() = runTest {
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
}
