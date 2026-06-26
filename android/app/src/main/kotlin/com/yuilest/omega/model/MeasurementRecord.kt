package com.yuilest.omega.model

/** 측정 1건. 좌표가 없으면 극좌표에서 환산해 채운다. */
data class MeasurementRecord(
    val seq: Int,
    val pointId: String = "",
    var haDeg: Double? = null,
    var vaDeg: Double? = null,
    var sdM: Double? = null,
    var x: Double? = null,
    var y: Double? = null,
    var z: Double? = null,
    val sourceFormat: SourceFormat = SourceFormat.UNKNOWN,
    var raw: String = ""
)

enum class SourceFormat(val label: String) {
    SDR33("SDR33"), GTS7("GTS-7"), CSV("CSV"), UNKNOWN("UNKNOWN")
}
