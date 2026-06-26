package com.yuilest.omega.workflow

import com.yuilest.omega.geometry.Helmert2DResult
import com.yuilest.omega.geometry.Helmert2DSolver
import com.yuilest.omega.geometry.KabschSolver
import com.yuilest.omega.geometry.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 오메가 2차 작업 워크플로우 (안드로이드). Windows 측 omega/workflow.py 와 동일 로직.
 * 순수 로직만 담아 GeometryTest/WorkflowTest 로 CI 검증한다(UI 무관).
 */

// ---------------- 자유측점 (이동측설) ----------------
data class BacksightPair(
    val pointId: String,
    val local: Pair<Double, Double>,
    val global: Pair<Double, Double>
)

class FreeStationSession {
    val pairs = ArrayList<BacksightPair>()
    var result: Helmert2DResult? = null
        private set

    fun addPair(pointId: String, local: Pair<Double, Double>, global: Pair<Double, Double>) {
        pairs.add(BacksightPair(pointId, local, global))
    }

    fun clear() { pairs.clear(); result = null }

    fun solve(): Helmert2DResult {
        require(pairs.size >= 2) { "후시점 2점 이상 필요" }
        val r = Helmert2DSolver.solve(pairs.map { it.local }, pairs.map { it.global })
        result = r
        return r
    }

    /** 각 후시점 변환 후 잔차(mm). */
    fun residualsMm(): List<Pair<String, Double>> {
        val r = result ?: return emptyList()
        return pairs.map { p ->
            val (tx, ty) = r.apply(p.local.first, p.local.second)
            val dx = tx - p.global.first; val dy = ty - p.global.second
            p.pointId to sqrt(dx * dx + dy * dy) * 1000.0
        }
    }

    fun transform(x: Double, y: Double): Pair<Double, Double> {
        val r = result ?: error("먼저 solve() 호출 필요")
        return r.apply(x, y)
    }

    /** 축척이 1 에서 과도 이탈 시 경고(ppm). */
    fun scaleWarning(tolPpm: Double = 200.0): String? {
        val r = result ?: return null
        val ppm = abs(r.scale - 1.0) * 1e6
        return if (ppm > tolPpm) "축척 편차 ${"%.0f".format(ppm)} ppm — 후시점/입력 확인 필요" else null
    }
}

// ---------------- 블록 best-fit (합격판정) ----------------
data class FitRow(
    val pointId: String,
    val design: Vec3,
    val measured: Vec3
)

data class FitReport(
    val rmsMm: Double,
    val maxMm: Double,
    val overallPass: Boolean,
    val perPoint: List<Triple<String, Double, Boolean>>
)

class BlockFitSession(var tolMm: Double = 3.0) {
    val rows = ArrayList<FitRow>()

    fun addRow(pointId: String, design: Vec3, measured: Vec3) {
        rows.add(FitRow(pointId, design, measured))
    }

    fun clear() { rows.clear() }

    fun run(): FitReport {
        require(rows.size >= 3) { "정합에는 3점 이상 필요" }
        val res = KabschSolver.solve(rows.map { it.measured }, rows.map { it.design })
        var maxMm = 0.0
        val per = ArrayList<Triple<String, Double, Boolean>>()
        for (i in rows.indices) {
            val mm = res.perPointResiduals[i] * 1000.0
            val ok = mm <= tolMm
            per.add(Triple(rows[i].pointId, mm, ok))
            if (mm > maxMm) maxMm = mm
        }
        val overall = per.all { it.third }
        return FitReport(res.rmsResidual * 1000.0, maxMm, overall, per)
    }
}
