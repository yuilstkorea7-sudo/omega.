package com.yuilest.omega.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.util.UUID
import kotlin.concurrent.thread

/**
 * FX201 과의 Bluetooth Classic SPP 연결 관리.
 * - 표준 SPP UUID 로 RFCOMM 소켓 연결
 * - 줄 단위 버퍼링 후 onLine 콜백 (백그라운드 스레드)
 * - 연결 끊김 시 지수 백오프 자동 재연결
 *
 * 콜백은 워커 스레드에서 호출되므로 UI 갱신은 메인 스레드로 위임할 것.
 */
@SuppressLint("MissingPermission")
class BluetoothSppManager(
    private val onLine: (String) -> Unit,
    private val onState: (ConnectionState) -> Unit
) {
    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    @Volatile private var running = false
    @Volatile private var device: BluetoothDevice? = null

    fun pairedDevices(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return adapter.bondedDevices?.toList() ?: emptyList()
    }

    fun connect(target: BluetoothDevice) {
        device = target
        running = true
        onState(ConnectionState.Connecting)
        thread(name = "omega-bt") { connectLoop() }
    }

    private fun connectLoop() {
        var backoff = 500L
        while (running) {
            val dev = device ?: break
            try {
                BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
                val s = dev.createRfcommSocketToServiceRecord(SPP_UUID)
                s.connect()
                socket = s
                onState(ConnectionState.Connected(dev.name ?: dev.address))
                backoff = 500L
                readLoop(s.inputStream)
            } catch (e: Exception) {
                onState(ConnectionState.Error(e.message ?: "연결 오류"))
            }
            if (!running) break
            try { Thread.sleep(backoff) } catch (_: InterruptedException) {}
            backoff = (backoff * 2).coerceAtMost(8000L)
        }
    }

    private fun readLoop(input: InputStream) {
        val buf = ByteArray(1024)
        val sb = StringBuilder()
        while (running) {
            val read = try { input.read(buf) } catch (e: Exception) { -1 }
            if (read < 0) break
            for (i in 0 until read) {
                val ch = buf[i].toInt().toChar()
                if (ch == '\n') {
                    val line = sb.toString().trim()
                    sb.setLength(0)
                    if (line.isNotEmpty()) onLine(line)
                } else if (ch != '\r') {
                    sb.append(ch)
                }
            }
        }
    }

    fun disconnect() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        onState(ConnectionState.Disconnected)
    }
}
