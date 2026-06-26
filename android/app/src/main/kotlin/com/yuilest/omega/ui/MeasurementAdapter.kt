package com.yuilest.omega.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yuilest.omega.R
import com.yuilest.omega.model.MeasurementRecord

class MeasurementAdapter : RecyclerView.Adapter<MeasurementAdapter.VH>() {
    private val items = ArrayList<MeasurementRecord>()

    /** 설정되면 X,Y 를 전역좌표로 변환해 표시(자유측점 적용). */
    var coordTransform: ((Double, Double) -> Pair<Double, Double>)? = null

    fun add(r: MeasurementRecord) {
        items.add(r)
        notifyItemInserted(items.size - 1)
    }

    fun clear() {
        val n = items.size
        items.clear()
        notifyItemRangeRemoved(0, n)
    }

    fun size() = items.size
    fun snapshot(): List<MeasurementRecord> = items.toList()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val seq: TextView = v.findViewById(R.id.col_seq)
        val pid: TextView = v.findViewById(R.id.col_pid)
        val ha: TextView = v.findViewById(R.id.col_ha)
        val va: TextView = v.findViewById(R.id.col_va)
        val sd: TextView = v.findViewById(R.id.col_sd)
        val xyz: TextView = v.findViewById(R.id.col_xyz)
        val fmt: TextView = v.findViewById(R.id.col_fmt)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_measurement, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.seq.text = r.seq.toString()
        holder.pid.text = r.pointId.ifEmpty { "—" }
        holder.ha.text = r.haDeg?.let { "%.4f".format(it) } ?: "—"
        holder.va.text = r.vaDeg?.let { "%.4f".format(it) } ?: "—"
        holder.sd.text = r.sdM?.let { "%.4f".format(it) } ?: "—"
        holder.xyz.text = if (r.x != null) {
            val t = coordTransform
            if (t != null) {
                val (gx, gy) = t(r.x!!, r.y!!)
                "%.3f, %.3f, %.3f".format(gx, gy, r.z)
            } else {
                "%.3f, %.3f, %.3f".format(r.x, r.y, r.z)
            }
        } else "(좌표 없음)"
        holder.fmt.text = r.sourceFormat.label
    }
}
