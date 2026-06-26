package com.yuilest.omega.geometry

import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.LUDecomposition
import org.apache.commons.math3.linear.MatrixUtils
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.linear.SingularValueDecomposition
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 오메가 좌표 변환 엔진 (안드로이드).
 * Windows 측 omega/geometry.py 와 동일한 알고리즘 — 검증은 파이썬 코어에서 완료,
 * 동일 수식을 Kotlin 으로 포팅하고 GeometryTest 로 회귀 검증한다.
 */

data class Vec3(val x: Double, val y: Double, val z: Double)

/** 광파기 극좌표(HA, VA=천정각, SD) → 기기 국소 직교좌표. */
fun polarToCartesian(haDeg: Double, vaDeg: Double, sdM: Double): Vec3 {
    val ha = Math.toRadians(haDeg)
    val va = Math.toRadians(vaDeg)
    val hd = sdM * sin(va)
    val dz = sdM * cos(va)
    return Vec3(hd * sin(ha), hd * cos(ha), dz)
}

data class KabschResult(
    val rotation: RealMatrix,        // 3x3
    val translation: DoubleArray,    // 3
    val rmsResidual: Double,
    val perPointResiduals: List<Double>
)

/**
 * 대응점 (pᵢ, qᵢ) 에 대해 minimize Σ ||R·pᵢ + t − qᵢ||² 를 푸는 강체 변환.
 * H = Σ(pᵢ−p̄)(qᵢ−q̄)ᵀ, SVD H=UΣVᵀ, d=sign(det(V·Uᵀ)), R=V·diag(1,1,d)·Uᵀ, t=q̄−R·p̄.
 */
object KabschSolver {
    fun solve(p: List<Vec3>, q: List<Vec3>): KabschResult {
        require(p.size == q.size) { "p, q 크기 불일치" }
        require(p.size >= 3) { "Kabsch 는 3점 이상 필요" }
        val n = p.size

        val pBar = centroid(p); val qBar = centroid(q)
        val h = Array(3) { DoubleArray(3) }
        for (i in 0 until n) {
            val pp = doubleArrayOf(p[i].x - pBar[0], p[i].y - pBar[1], p[i].z - pBar[2])
            val qq = doubleArrayOf(q[i].x - qBar[0], q[i].y - qBar[1], q[i].z - qBar[2])
            for (r in 0..2) for (c in 0..2) h[r][c] += pp[r] * qq[c]
        }
        val hMat: RealMatrix = Array2DRowRealMatrix(h)
        val svd = SingularValueDecomposition(hMat)
        val u = svd.u; val v = svd.v
        val vut = v.multiply(u.transpose())
        val det = LUDecomposition(vut).determinant
        val d = if (det >= 0) 1.0 else -1.0
        val diag = MatrixUtils.createRealDiagonalMatrix(doubleArrayOf(1.0, 1.0, d))
        val r: RealMatrix = v.multiply(diag).multiply(u.transpose())
        val t = doubleArrayOf(
            qBar[0] - dot(r.getRow(0), pBar),
            qBar[1] - dot(r.getRow(1), pBar),
            qBar[2] - dot(r.getRow(2), pBar)
        )

        val resid = ArrayList<Double>(n); var sse = 0.0
        for (i in 0 until n) {
            val pv = doubleArrayOf(p[i].x, p[i].y, p[i].z)
            val pred = doubleArrayOf(
                dot(r.getRow(0), pv) + t[0],
                dot(r.getRow(1), pv) + t[1],
                dot(r.getRow(2), pv) + t[2]
            )
            val dx = pred[0] - q[i].x; val dy = pred[1] - q[i].y; val dz = pred[2] - q[i].z
            val d2 = dx * dx + dy * dy + dz * dz
            resid.add(sqrt(d2)); sse += d2
        }
        return KabschResult(r, t, sqrt(sse / n), resid)
    }

    private fun centroid(v: List<Vec3>): DoubleArray {
        var sx = 0.0; var sy = 0.0; var sz = 0.0
        for (p in v) { sx += p.x; sy += p.y; sz += p.z }
        val n = v.size.toDouble()
        return doubleArrayOf(sx / n, sy / n, sz / n)
    }

    private fun dot(a: DoubleArray, b: DoubleArray) =
        a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
}

data class Helmert2DResult(val scale: Double, val rotationDeg: Double, val tx: Double, val ty: Double) {
    fun apply(x: Double, y: Double): Pair<Double, Double> {
        val a = Math.toRadians(rotationDeg)
        val c = scale * cos(a); val s = scale * sin(a)
        return Pair(c * x - s * y + tx, s * x + c * y + ty)
    }
}

/**
 * 2점 이상 대응으로 2D 닮음 변환(축척·회전·평행이동)을 최소제곱으로 구한다.
 * 자유측점 → 전역 좌표계 이동측설(leapfrog)용.
 */
object Helmert2DSolver {
    fun solve(src: List<Pair<Double, Double>>, dst: List<Pair<Double, Double>>): Helmert2DResult {
        require(src.size == dst.size && src.size >= 2) { "Helmert2D 는 2점 이상 대응 필요" }
        val rows = ArrayList<DoubleArray>(); val rhs = ArrayList<Double>()
        for (i in src.indices) {
            val (x, y) = src[i]; val (xp, yp) = dst[i]
            rows.add(doubleArrayOf(x, -y, 1.0, 0.0)); rhs.add(xp)
            rows.add(doubleArrayOf(y, x, 0.0, 1.0)); rhs.add(yp)
        }
        val a = Array2DRowRealMatrix(rows.toTypedArray())
        val svd = SingularValueDecomposition(a)
        val sol = svd.solver.solve(Array2DRowRealMatrix(rhs.toDoubleArray()))
        val av = sol.getEntry(0, 0); val bv = sol.getEntry(1, 0)
        val tx = sol.getEntry(2, 0); val ty = sol.getEntry(3, 0)
        return Helmert2DResult(hypot(av, bv), Math.toDegrees(atan2(bv, av)), tx, ty)
    }
}
