package com.yuilest.omega.protocol

import com.yuilest.omega.geometry.polarToCartesian
import com.yuilest.omega.model.MeasurementRecord
import com.yuilest.omega.model.SourceFormat

/**
 * FX201 데이터 줄 파서. Windows 측 omega/protocol.py 와 동일한 규칙.
 * detect → parse → 좌표 보정(극좌표면 환산).
 */
class ProtocolParser {
    private var seq = 0
    private fun next(): Int { seq += 1; return seq }

    private val numRe = Regex("[-+]?\\d*\\.?\\d+")

    fun detect(line: String): SourceFormat {
        val s = line.trim()
        if (s.startsWith("00NM") || s.startsWith("08KI") || s.startsWith("08TP") ||
            s.startsWith("09F1") || s.startsWith("13NM")) return SourceFormat.SDR33
        if (Regex("^\\d{1,4},").containsMatchIn(s) && s.count { it == ',' } >= 3) return SourceFormat.GTS7
        if (s.contains(",")) return SourceFormat.CSV
        return SourceFormat.UNKNOWN
    }

    fun parseLine(line: String): MeasurementRecord? {
        val s = line.trim()
        if (s.isEmpty()) return null
        val rec = when (detect(s)) {
            SourceFormat.SDR33 -> parseSdr33(s)
            SourceFormat.GTS7 -> parseCsvLike(s, SourceFormat.GTS7)
            SourceFormat.CSV -> parseCsvLike(s, SourceFormat.CSV)
            else -> null
        } ?: return null
        rec.raw = line.trimEnd('\r', '\n')
        ensureCoords(rec)
        return rec
    }

    private fun ensureCoords(r: MeasurementRecord) {
        if (r.x == null && r.haDeg != null && r.vaDeg != null && r.sdM != null) {
            val v = polarToCartesian(r.haDeg!!, r.vaDeg!!, r.sdM!!)
            r.x = v.x; r.y = v.y; r.z = v.z
        }
    }

    private fun parseSdr33(s: String): MeasurementRecord? {
        if (s.startsWith("08KI")) {
            val body = s.substring(4)
            val pid = body.take(16).trim()
            val nums = numRe.findAll(body.drop(16)).map { it.value.toDouble() }.toList()
            if (nums.size >= 3) {
                return MeasurementRecord(next(), pid, x = nums[1], y = nums[0], z = nums[2],
                    sourceFormat = SourceFormat.SDR33)
            }
        }
        if (s.startsWith("09F1")) {
            val nums = numRe.findAll(s.substring(4)).map { it.value.toDouble() }.toList()
            if (nums.size >= 3) {
                val n = nums.size
                return MeasurementRecord(next(), haDeg = nums[n - 3], vaDeg = nums[n - 2],
                    sdM = nums[n - 1], sourceFormat = SourceFormat.SDR33)
            }
        }
        return null
    }

    private fun parseCsvLike(s: String, fmt: SourceFormat): MeasurementRecord? {
        val parts = s.split(",").map { it.trim() }
        if (parts.size < 4) return null
        val pid = parts[0]
        val v = parts.drop(1).map { it.toDoubleOrNull() }
        val rec = MeasurementRecord(next(), pid, sourceFormat = fmt)
        if (parts.size >= 7 && v.take(6).none { it == null }) {
            rec.haDeg = v[0]; rec.vaDeg = v[1]; rec.sdM = v[2]
            rec.x = v[3]; rec.y = v[4]; rec.z = v[5]
        } else {
            val a = v[0]; val b = v[1]; val c = v[2]
            val looksPolar = a != null && a in 0.0..360.0 && b != null && b in 0.0..180.0
            if (looksPolar) { rec.haDeg = a; rec.vaDeg = b; rec.sdM = c }
            else { rec.x = a; rec.y = b; rec.z = c }
        }
        return rec
    }
}
