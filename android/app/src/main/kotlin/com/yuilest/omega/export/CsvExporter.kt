package com.yuilest.omega.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.yuilest.omega.model.MeasurementRecord
import java.io.File

/** 측정 목록을 한국어 엑셀 호환(UTF-8 BOM) CSV 로 저장하고 공유 인텐트를 띄운다. */
object CsvExporter {
    fun export(context: Context, records: List<MeasurementRecord>): Intent {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append("순번,점번호,HA(°),VA(°),SD(m),X,Y,Z,포맷\n")
        for (r in records) {
            sb.append(r.seq).append(',')
                .append(r.pointId).append(',')
                .append(r.haDeg?.let { "%.4f".format(it) } ?: "").append(',')
                .append(r.vaDeg?.let { "%.4f".format(it) } ?: "").append(',')
                .append(r.sdM?.let { "%.4f".format(it) } ?: "").append(',')
                .append(r.x?.let { "%.4f".format(it) } ?: "").append(',')
                .append(r.y?.let { "%.4f".format(it) } ?: "").append(',')
                .append(r.z?.let { "%.4f".format(it) } ?: "").append(',')
                .append(r.sourceFormat.label).append('\n')
        }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "omega_measurements.csv")
        file.writeBytes(sb.toString().toByteArray(Charsets.UTF_8))

        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
