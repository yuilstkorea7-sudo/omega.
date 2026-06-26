package com.yuilest.omega.workflow

import com.yuilest.omega.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class WorkflowTest {

    @Test fun freeStationTransformsToGlobal() {
        val th = Math.toRadians(20.0); val c = cos(th); val s = sin(th)
        fun g(x: Double, y: Double) = Pair(c * x - s * y + 1000.0, s * x + c * y + 2000.0)
        val fs = FreeStationSession()
        listOf("BS1" to Pair(0.0, 0.0), "BS2" to Pair(8.0, 0.0), "BS3" to Pair(0.0, 6.0))
            .forEach { (id, loc) -> fs.addPair(id, loc, g(loc.first, loc.second)) }
        val r = fs.solve()
        assertEquals(1.0, r.scale, 1e-6)
        assertEquals(20.0, r.rotationDeg, 1e-6)
        fs.residualsMm().forEach { assertTrue(it.second < 1e-3) }
        val (gx, gy) = fs.transform(3.0, 7.0)
        val (egx, egy) = g(3.0, 7.0)
        assertEquals(egx, gx, 1e-6); assertEquals(egy, gy, 1e-6)
    }

    @Test fun blockFitPassWithinTolerance() {
        val design = listOf(Vec3(0.0,0.0,0.0), Vec3(5.0,0.0,0.0), Vec3(5.0,3.0,0.0),
            Vec3(0.0,3.0,1.0), Vec3(2.5,1.5,0.5))
        val fit = BlockFitSession(3.0)
        design.forEachIndexed { i, d ->
            fit.addRow("P${i+1}", d, Vec3(d.x + 0.0008, d.y - 0.0006, d.z + 0.0007))
        }
        val rep = fit.run()
        assertTrue(rep.overallPass)
        assertTrue(rep.maxMm < 3.0)
    }

    @Test fun blockFitFailsWhenPointOff() {
        val design = listOf(Vec3(0.0,0.0,0.0), Vec3(5.0,0.0,0.0), Vec3(5.0,3.0,0.0),
            Vec3(0.0,3.0,0.0), Vec3(2.5,1.5,0.0))
        val fit = BlockFitSession(3.0)
        design.forEachIndexed { i, d ->
            val m = if (i == 2) Vec3(d.x + 0.010, d.y, d.z) else d
            fit.addRow("P${i+1}", d, m)
        }
        val rep = fit.run()
        assertFalse(rep.overallPass)
        assertTrue(rep.maxMm > 3.0)
    }
}
