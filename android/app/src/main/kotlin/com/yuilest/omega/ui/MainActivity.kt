package com.yuilest.omega.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.yuilest.omega.bluetooth.BluetoothSppManager
import com.yuilest.omega.bluetooth.ConnectionState
import com.yuilest.omega.bluetooth.SimulatorSource
import com.yuilest.omega.databinding.ActivityMainBinding
import com.yuilest.omega.export.CsvExporter
import com.yuilest.omega.geometry.Vec3
import com.yuilest.omega.model.MeasurementRecord
import com.yuilest.omega.protocol.ProtocolParser
import com.yuilest.omega.workflow.BlockFitSession
import com.yuilest.omega.workflow.FreeStationSession

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val parser = ProtocolParser()
    private val adapter = MeasurementAdapter()
    private val main = Handler(Looper.getMainLooper())

    private val fs = FreeStationSession()
    private val bf = BlockFitSession()
    private var lastRecord: MeasurementRecord? = null

    private var connected = false
    private var simSource: SimulatorSource? = null
    private val bt by lazy {
        BluetoothSppManager(
            onLine = { line -> main.post { onLine(line) } },
            onState = { st -> main.post { onState(st) } }
        )
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) showDevicePicker()
        else toast("블루투스 권한이 필요합니다.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.sourceSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("시뮬레이터", "블루투스 (FX201)")
        )

        setupTabs()
        binding.btnConnect.setOnClickListener { toggleConnect() }
        binding.btnClear.setOnClickListener {
            adapter.clear(); binding.count.text = "측정 0건"
        }
        binding.btnExport.setOnClickListener { exportCsv() }
        binding.chkGlobal.setOnCheckedChangeListener { _, checked -> onGlobalToggle(checked) }

        binding.btnFsAdd.setOnClickListener { fsAddFromLast() }
        binding.btnFsSolve.setOnClickListener { fsSolve() }
        binding.btnFsClear.setOnClickListener { fsClear() }

        binding.btnBfAdd.setOnClickListener { bfAdd() }
        binding.btnBfFill.setOnClickListener { bfFillMeasured() }
        binding.btnBfRun.setOnClickListener { bfRun() }
        binding.btnBfClear.setOnClickListener { bfClear() }

        updateStatus(ConnectionState.Disconnected)
    }

    // ----- 탭 -----
    private fun setupTabs() {
        val tl = binding.tabLayout
        tl.addTab(tl.newTab().setText("실시간 측정"))
        tl.addTab(tl.newTab().setText("자유측점"))
        tl.addTab(tl.newTab().setText("블록 정합"))
        tl.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.containerLive.visibility = if (tab.position == 0) View.VISIBLE else View.GONE
                binding.containerFree.visibility = if (tab.position == 1) View.VISIBLE else View.GONE
                binding.containerBlock.visibility = if (tab.position == 2) View.VISIBLE else View.GONE
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    // ----- 연결 -----
    private fun toggleConnect() {
        if (connected) { stopSource(); return }
        if (binding.sourceSpinner.selectedItemPosition == 0) startSimulator()
        else requestThenPick()
    }

    private fun startSimulator() {
        val s = SimulatorSource(
            onLine = { line -> main.post { onLine(line) } },
            onState = { st -> main.post { onState(st) } }
        )
        simSource = s
        s.start()
    }

    private fun stopSource() {
        simSource?.stop(); simSource = null
        bt.disconnect()
    }

    private fun requestThenPick() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = perms.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) permLauncher.launch(perms) else showDevicePicker()
    }

    @SuppressLint("MissingPermission")
    private fun showDevicePicker() {
        val devices = bt.pairedDevices()
        if (devices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("페어링된 기기 없음")
                .setMessage("설정 → 블루투스 에서 FX201 을 먼저 페어링하세요.\nPIN: 0000 또는 1234")
                .setPositiveButton("확인", null).show()
            return
        }
        val labels = devices.map { "${it.name ?: "(이름없음)"}  ${it.address}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("FX201 선택")
            .setItems(labels) { _, i -> bt.connect(devices[i]) }.show()
    }

    private fun onLine(line: String) {
        val rec = parser.parseLine(line) ?: return
        adapter.add(rec)
        lastRecord = rec
        binding.list.scrollToPosition(adapter.size() - 1)
        binding.count.text = "측정 ${adapter.size()}건"
    }

    private fun onState(st: ConnectionState) {
        connected = st is ConnectionState.Connected
        updateStatus(st)
        binding.btnConnect.text = if (connected) "연결 해제" else "연결"
    }

    private fun updateStatus(st: ConnectionState) {
        binding.status.text = when (st) {
            is ConnectionState.Disconnected -> "● 대기"
            is ConnectionState.Connecting -> "● 연결 중…"
            is ConnectionState.Connected -> "● 연결됨 (${st.deviceName})"
            is ConnectionState.Error -> "● 오류: ${st.message}"
        }
    }

    // ----- 실시간 탭 -----
    private fun onGlobalToggle(checked: Boolean) {
        if (checked && fs.result == null) {
            toast("먼저 [자유측점] 탭에서 변환을 계산하세요.")
            binding.chkGlobal.isChecked = false
            return
        }
        adapter.coordTransform = if (checked) { x, y -> fs.transform(x, y) } else null
        adapter.notifyDataSetChanged()
    }

    private fun exportCsv() {
        if (adapter.size() == 0) { toast("측정 데이터가 없습니다."); return }
        val intent = CsvExporter.export(this, adapter.snapshot())
        startActivity(Intent.createChooser(intent, "CSV 공유"))
    }

    // ----- 자유측점 탭 -----
    private fun fsAddFromLast() {
        val r = lastRecord
        if (r?.x == null) { toast("먼저 측정값을 수신하세요."); return }
        val gx = binding.fsGx.text.toString().toDoubleOrNull()
        val gy = binding.fsGy.text.toString().toDoubleOrNull()
        if (gx == null || gy == null) { toast("전역 X, Y 를 숫자로 입력하세요."); return }
        val pid = binding.fsPid.text.toString().ifBlank { r.pointId.ifBlank { "BS${fs.pairs.size + 1}" } }
        fs.addPair(pid, Pair(r.x!!, r.y!!), Pair(gx, gy))
        binding.fsPid.text = null; binding.fsGx.text = null; binding.fsGy.text = null
        renderFsList()
    }

    private fun renderFsList() {
        val sb = StringBuilder("등록 후시점 (${fs.pairs.size})\n")
        for (p in fs.pairs) {
            sb.append("· ${p.pointId}  전역(${"%.3f".format(p.global.first)}, ${"%.3f".format(p.global.second)})")
                .append("  국소(${"%.3f".format(p.local.first)}, ${"%.3f".format(p.local.second)})\n")
        }
        binding.fsList.text = sb.toString()
    }

    private fun fsSolve() {
        try {
            val r = fs.solve()
            val sb = StringBuilder()
            sb.append("축척 ${"%.6f".format(r.scale)}   회전 ${"%.4f".format(r.rotationDeg)}°   ")
                .append("이동 (${"%.4f".format(r.tx)}, ${"%.4f".format(r.ty)})\n")
            fs.scaleWarning()?.let { sb.append("⚠ ").append(it).append("\n") }
            sb.append("후시 잔차: ")
                .append(fs.residualsMm().joinToString(", ") { "${it.first} ${"%.1f".format(it.second)}mm" })
                .append("\n→ [실시간 측정] 탭의 '전역좌표 표시'를 켜면 이후 점이 전역좌표로 환산됩니다.")
            binding.fsResult.text = sb.toString()
        } catch (e: Exception) {
            toast(e.message ?: "계산 불가")
        }
    }

    private fun fsClear() {
        fs.clear()
        binding.fsList.text = ""
        binding.fsResult.text = "후시점을 등록하고 [변환 계산]을 누르세요."
        binding.chkGlobal.isChecked = false
        adapter.coordTransform = null
        adapter.notifyDataSetChanged()
    }

    // ----- 블록 정합 탭 -----
    private fun bfFillMeasured() {
        val r = lastRecord
        if (r?.x == null) { toast("먼저 측정값을 수신하세요."); return }
        binding.bfMx.setText("%.4f".format(r.x))
        binding.bfMy.setText("%.4f".format(r.y))
        binding.bfMz.setText("%.4f".format(r.z))
        if (binding.bfPid.text.isNullOrBlank()) binding.bfPid.setText(r.pointId)
    }

    private fun bfAdd() {
        val dx = binding.bfDx.text.toString().toDoubleOrNull()
        val dy = binding.bfDy.text.toString().toDoubleOrNull()
        val dz = binding.bfDz.text.toString().toDoubleOrNull()
        val mx = binding.bfMx.text.toString().toDoubleOrNull()
        val my = binding.bfMy.text.toString().toDoubleOrNull()
        val mz = binding.bfMz.text.toString().toDoubleOrNull()
        if (listOf(dx, dy, dz, mx, my, mz).any { it == null }) {
            toast("설계/실측 X,Y,Z 를 숫자로 입력하세요."); return
        }
        val pid = binding.bfPid.text.toString().ifBlank { "P${bf.rows.size + 1}" }
        bf.addRow(pid, Vec3(dx!!, dy!!, dz!!), Vec3(mx!!, my!!, mz!!))
        for (e in listOf(binding.bfPid, binding.bfDx, binding.bfDy, binding.bfDz,
                binding.bfMx, binding.bfMy, binding.bfMz)) e.text = null
        renderBfList(null)
    }

    private fun renderBfList(report: com.yuilest.omega.workflow.FitReport?) {
        val sb = StringBuilder("입력 점 (${bf.rows.size})\n")
        val residMap = report?.perPoint?.associate { it.first to Pair(it.second, it.third) }
        for (row in bf.rows) {
            sb.append("· ${row.pointId}  설계(${"%.3f".format(row.design.x)}, ${"%.3f".format(row.design.y)}, ${"%.3f".format(row.design.z)})")
            val r = residMap?.get(row.pointId)
            if (r != null) sb.append("   편차 ${"%.2f".format(r.first)}mm  ${if (r.second) "합격" else "불합격"}")
            sb.append("\n")
        }
        binding.bfList.text = sb.toString()
    }

    private fun bfRun() {
        bf.tolMm = binding.bfTol.text.toString().toDoubleOrNull() ?: 3.0
        try {
            val rep = bf.run()
            renderBfList(rep)
            val verdict = if (rep.overallPass) "합격(PASS)" else "불합격(FAIL)"
            binding.bfResult.setTextColor(if (rep.overallPass) 0xFF1D9E75.toInt() else 0xFFC0392B.toInt())
            binding.bfResult.text = "전체 판정: $verdict   RMS ${"%.2f".format(rep.rmsMm)}mm   " +
                "최대편차 ${"%.2f".format(rep.maxMm)}mm   (허용 ${"%.1f".format(bf.tolMm)}mm)"
        } catch (e: Exception) {
            toast(e.message ?: "정합 불가")
        }
    }

    private fun bfClear() {
        bf.clear()
        binding.bfList.text = ""
        binding.bfResult.setTextColor(0xFF000000.toInt())
        binding.bfResult.text = "행을 추가하고 [정합 실행]을 누르세요."
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        stopSource()
    }
}
