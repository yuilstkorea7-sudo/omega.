package com.yuilest.omega.bluetooth

import kotlin.concurrent.thread
import kotlin.random.Random

/** 입력 소스 공통 인터페이스 — Bluetooth 또는 시뮬레이터. */
interface MeasurementSource {
    fun start()
    fun stop()
}

/**
 * 하드웨어 없이 FX201 형식의 줄(점번호, HA, VA, SD)을 주기적으로 생성.
 * 갤럭시 탭에 설치 직후 FX201 없이 전체 동작을 즉시 test 할 수 있게 한다.
 */
class SimulatorSource(
    private val intervalMs: Long = 1200L,
    private val onLine: (String) -> Unit,
    private val onState: (ConnectionState) -> Unit
) : MeasurementSource {
    @Volatile private var running = false
    private var n = 0
    private val rng = Random(System.currentTimeMillis())

    override fun start() {
        running = true
        onState(ConnectionState.Connected("시뮬레이터"))
        thread(name = "omega-sim") {
            while (running) {
                n += 1
                val ha = "%.4f".format(rng.nextDouble(0.0, 360.0))
                val va = "%.4f".format(90.0 + rng.nextDouble(-3.0, 3.0))
                val sd = "%.4f".format(rng.nextDouble(2.0, 12.0))
                onLine("P%03d,%s,%s,%s".format(n, ha, va, sd))
                try { Thread.sleep(intervalMs) } catch (_: InterruptedException) { break }
            }
        }
    }

    override fun stop() {
        running = false
        onState(ConnectionState.Disconnected)
    }
}
