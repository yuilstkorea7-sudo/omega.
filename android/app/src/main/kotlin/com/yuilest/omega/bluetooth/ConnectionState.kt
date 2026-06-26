package com.yuilest.omega.bluetooth

/** Bluetooth SPP 연결 상태 모델. UI 는 이 sealed class 만 관찰하면 된다. */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
