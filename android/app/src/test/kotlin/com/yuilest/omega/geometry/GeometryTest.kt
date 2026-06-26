package com.yuilest.omega.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.apache.commons.math3.linear.LUDecomposition
import kotlin.math.cos
import kotlin.math.sin

class GeometryTest {

    @Test fun polarHorizontal() {
        val v = polarToCartesian(0.0, 90.0, 5.0)
        assertEquals(0.0, v.x, 1e-9)
        assertEquals(5.0, v.y, 1e-9)
        assertEquals(0.0, v.z, 1e-9)
    }

    @Test fun polarEast() {
        val v = polarToCartesian(90.0, 90.0, 3.0)
        assertEquals(3.0, v.x, 1e-9)
        assertEquals(0.0, v.y, 1e-9)
    }

    @Test fun kabschRecoversKnownTransform() {
        val a = Math.toRadians(45.0)
        // 45° about z + translation (2, -3, 1.5)
        val p = listOf(
            Vec3(1.0, 0.0, 0.0), Vec3(0.0, 2.0, 0.0),
            Vec3(0.0, 0.0, 3.0), Vec3(1.0, 1.0, 1.0)
        )
        fun rot(v: Vec3) = Vec3(
            cos(a) * v.x - sin(a) * v.y + 2.0,
            sin(a) * v.x + cos(a) * v.y - 3.0,
            v.z + 1.5
        )
        val q = p.map { rot(it) }
        val res = KabschSolver.solve(p, q)
        assertTrue("RMS too high: ${res.rmsResidual}", res.rmsResidual < 1e-6)
        val det = LUDecomposition(res.rotation).determinant
        assertTrue("not proper rotation", det > 0)
        assertEquals(2.0, res.translation[0], 1e-6)
        assertEquals(-3.0, res.translation[1], 1e-6)
        assertEquals(1.5, res.translation[2], 1e-6)
    }

    @Test fun helmert2dRecovers() {
        val s = 2.0; val th = Math.toRadians(30.0)
        val c = s * cos(th); val sn = s * sin(th)
        val src = listOf(0.0 to 0.0, 10.0 to 0.0, 10.0 to 10.0, 0.0 to 10.0)
        val dst = src.map { (x, y) -> (c * x - sn * y + 5.0) to (sn * x + c * y - 4.0) }
        val r = Helmert2DSolver.solve(src, dst)
        assertEquals(2.0, r.scale, 1e-6)
        assertEquals(30.0, r.rotationDeg, 1e-6)
        assertEquals(5.0, r.tx, 1e-6)
        assertEquals(-4.0, r.ty, 1e-6)
    }
}
