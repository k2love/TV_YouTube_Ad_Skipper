package com.example.tvadskipper

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.tvadskipper.databinding.ActivityMainBinding
import androidx.appcompat.app.AlertDialog
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import android.os.Build

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var loungeManager: LoungeManager
    private lateinit var updateManager: UpdateManager
    private var hasAutoCheckedUpdate = false
    private var isCheckingUpdate = false
    private var isDownloadingApk = false
    private var downloadedApkFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.init(applicationContext)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(TvAdSkipService.PREFS_NAME, Context.MODE_PRIVATE)
        loungeManager = LoungeManager.getInstance(this)
        updateManager = UpdateManager(this)

        setupLoungeListeners()
        setupAccessibilityListeners()
        setupUpdateListeners()
        setupLogListeners()

        // 이미 페어링되어 있다면 Lounge 백그라운드 서비스 시작
        if (loungeManager.isPaired()) {
            startLoungeService()
        }
    }

    override fun onResume() {
        super.onResume()
        updateLoungeUI()
        updateAccessibilityUI()

        // 앱 시작 시 백그라운드에서 1회 자동 업데이트 확인
        if (!hasAutoCheckedUpdate) {
            hasAutoCheckedUpdate = true
            performCheckUpdate(isManual = false)
        }
    }

    private fun setupLoungeListeners() {
        binding.btnPair.setOnClickListener {
            val code = binding.etPairingCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "TV 코드를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnPair.isEnabled = false
            binding.btnPair.text = "연결 중..."

            lifecycleScope.launch {
                val result = loungeManager.pairWithCode(code)
                binding.btnPair.isEnabled = true
                binding.btnPair.text = getString(R.string.btn_pair_tv)

                result.onSuccess { deviceName ->
                    Toast.makeText(this@MainActivity, "연결 성공: $deviceName", Toast.LENGTH_LONG).show()
                    binding.etPairingCode.setText("")
                    startLoungeService()
                    updateLoungeUI()
                }.onFailure { error ->
                    Toast.makeText(this@MainActivity, "연결 실패: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnUnpair.setOnClickListener {
            loungeManager.unpair()
            stopLoungeService()
            updateLoungeUI()
            Toast.makeText(this, "TV 연결이 해제되었습니다.", Toast.LENGTH_SHORT).show()
        }
        binding.switchPixelSensor.isChecked = prefs.getBoolean(TvAdSkipService.KEY_PIXEL_SENSOR_ENABLED, true)
        binding.switchPixelSensor.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(TvAdSkipService.KEY_PIXEL_SENSOR_ENABLED, isChecked).apply()
        }

        binding.switchAutoMute.isChecked = loungeManager.isAutoMuteEnabled()
        binding.switchAutoMute.setOnCheckedChangeListener { _, isChecked ->
            loungeManager.setAutoMuteEnabled(isChecked)
        }

        binding.switchShortsDismiss.isChecked = prefs.getBoolean(TvAdSkipService.KEY_SHORTS_AUTO_DISMISS, true)
        binding.switchShortsDismiss.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(TvAdSkipService.KEY_SHORTS_AUTO_DISMISS, isChecked).apply()
        }

        binding.switchQrAdDismiss.isChecked = prefs.getBoolean(TvAdSkipService.KEY_QR_AD_AUTO_DISMISS, true)
        binding.switchQrAdDismiss.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(TvAdSkipService.KEY_QR_AD_AUTO_DISMISS, isChecked).apply()
        }

        loungeManager.onStatusChanged = { isConnected, statusMsg ->
            runOnUiThread {
                binding.tvLoungeEventDesc.text = "상태: $statusMsg"
            }
        }

        loungeManager.onAutoMuteCallback = { isMuted, volume ->
            runOnUiThread {
                if (isMuted) {
                    binding.tvLoungeEventDesc.text = "🔇 강제 광고 감지: 하드웨어 자동 음소거 적용 중"
                } else {
                    binding.tvLoungeEventDesc.text = "🔊 본 영상 시작: 볼륨($volume) 복원 완료"
                }
            }
        }
    }

    private fun startLoungeService() {
        val intent = Intent(this, LoungeSkipService::class.java)
        startService(intent)
    }

    private fun stopLoungeService() {
        val intent = Intent(this, LoungeSkipService::class.java)
        stopService(intent)
    }

    private fun updateLoungeUI() {
        val isPaired = loungeManager.isPaired()
        val count = loungeManager.getSkipCount()

        binding.tvLoungeStats.text = getString(R.string.lounge_stats, count)
        binding.tvLoungeEventDesc.text = "최근 수신 신호: " + loungeManager.getLastEventDesc()

        if (isPaired) {
            val deviceName = loungeManager.getPairedDeviceName()
            binding.tvLoungeStatus.text = getString(R.string.lounge_status_paired, deviceName)
            binding.tvLoungeStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            binding.layoutPairingInput.visibility = View.GONE
            binding.btnUnpair.visibility = View.VISIBLE
        } else {
            binding.tvLoungeStatus.text = getString(R.string.lounge_status_unpaired)
            binding.tvLoungeStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
            binding.layoutPairingInput.visibility = View.VISIBLE
            binding.btnUnpair.visibility = View.GONE
        }
    }

    private fun setupAccessibilityListeners() {
        binding.btnOpenSettings.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
                fallbackIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(fallbackIntent)
            }
        }

        binding.btnRefresh.setOnClickListener {
            loungeManager.forceRestoreAudio()
            updateLoungeUI()
            updateAccessibilityUI()
            Toast.makeText(this, "상태 및 오디오가 갱신되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAccessibilityUI() {
        val isEnabled = isAccessibilityServiceEnabled()

        if (isEnabled) {
            binding.tvServiceStatus.text = getString(R.string.status_enabled)
            binding.tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            binding.tvServiceStatus.text = getString(R.string.status_disabled)
            binding.tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        if (TvAdSkipService.isServiceRunning) {
            return true
        }

        val expectedServiceName = "${packageName}/${TvAdSkipService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedServiceName, ignoreCase = true) ||
                componentName.equals("${packageName}/.TvAdSkipService", ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    private fun setupUpdateListeners() {
        // 앱 현재 버전 표시
        val currentVersion = "현재 버전: v${updateManager.getCurrentVersionName()} (Code ${updateManager.getCurrentVersionCode()})"
        binding.tvAppVersion.text = currentVersion

        // 수동 업데이트 확인 및 즉시 설치 버튼
        binding.btnCheckUpdate.setOnClickListener {
            val apk = downloadedApkFile
            if (apk != null && apk.exists() && apk.length() > 0) {
                executeInstallApk(apk)
            } else {
                performCheckUpdate(isManual = true)
            }
        }
    }

    private fun performCheckUpdate(isManual: Boolean) {
        if (isCheckingUpdate || isDownloadingApk) return
        isCheckingUpdate = true
        binding.tvUpdateStatus.text = "상태: 구글 드라이브 확인 중..."

        lifecycleScope.launch {
            val result = updateManager.checkRemoteApkUpdated()
            isCheckingUpdate = false

            if (isManual) {
                binding.btnCheckUpdate.requestFocus()
            }

            result.onSuccess { isUpdated ->
                if (isUpdated) {
                    binding.tvUpdateStatus.text = "상태: 새 빌드 발견! (다운로드 가능)"
                    showUpdateConfirmDialog(
                        title = "🚀 최신 버전 업데이트 발견",
                        message = "구글 드라이브에 새로운 빌드가 감지되었습니다.\n지금 다운로드하여 업데이트하시겠습니까?"
                    )
                } else {
                    binding.tvUpdateStatus.text = "상태: 최신 상태 유지 중 (v${updateManager.getCurrentVersionName()})"
                    if (isManual) {
                        Toast.makeText(this@MainActivity, "현재 최신 버전(v${updateManager.getCurrentVersionName()})을 사용 중입니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }.onFailure { error ->
                binding.tvUpdateStatus.text = "상태: 확인 실패 (${error.message})"
                if (isManual) {
                    Toast.makeText(this@MainActivity, "확인 실패: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showUpdateConfirmDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("지금 업데이트") { _, _ ->
                startDownloadAndInstall()
            }
            .setNegativeButton("취소") { _, _ ->
                binding.btnCheckUpdate.requestFocus()
            }
            .setOnDismissListener {
                binding.btnCheckUpdate.requestFocus()
            }
            .show()
    }

    private fun startDownloadAndInstall() {
        if (isDownloadingApk) return
        isDownloadingApk = true
        binding.tvUpdateStatus.text = "상태: APK 다운로드 중..."

        // 프로그레스 다이얼로그 레이아웃 생성
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val tvStatus = TextView(this).apply {
            text = "업데이트 APK 다운로드 중... (0%)"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
        }

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 20
            }
        }

        layout.addView(tvStatus)
        layout.addView(progressBar)

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("APK 다운로드")
            .setView(layout)
            .setCancelable(false)
            .create()

        progressDialog.show()

        lifecycleScope.launch {
            val downloadResult = updateManager.downloadApk { progress ->
                runOnUiThread {
                    progressBar.progress = progress
                    tvStatus.text = "업데이트 APK 다운로드 중... ($progress%)"
                }
            }

            isDownloadingApk = false

            downloadResult.onSuccess { apkFile ->
                downloadedApkFile = apkFile
                runOnUiThread {
                    tvStatus.text = "다운로드 완료! 설치 화면으로 이동합니다..."
                    progressBar.progress = 100
                }

                // 1. 사용자 피드백 대기 후 다운로드 창 닫기
                kotlinx.coroutines.delay(400L)
                progressDialog.dismiss()

                // 2. 포커스 복원 및 버튼 텍스트를 즉시 설치 가능 상태로 갱신
                val verInfo = updateManager.getApkVersionInfo(apkFile)
                val verName = verInfo?.second ?: "최신 버전"
                binding.btnCheckUpdate.text = "지금 설치하기 ($verName)"
                binding.tvUpdateStatus.text = "상태: 다운로드 완료 (설치 화면 실행 중)"
                binding.btnCheckUpdate.requestFocus()

                // 3. 윈도우 스택이 깨끗해진 상태에서 500ms 후 시스템 설치 화면 단독 직행!
                kotlinx.coroutines.delay(500L)
                executeInstallApk(apkFile)

            }.onFailure { error ->
                progressDialog.dismiss()
                binding.btnCheckUpdate.requestFocus()
                binding.tvUpdateStatus.text = "상태: 다운로드 실패"
                Toast.makeText(this@MainActivity, "다운로드 실패: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun executeInstallApk(apkFile: File) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            Toast.makeText(this, "설치 파일이 손상되었거나 없습니다. 다시 다운로드해 주세요.", Toast.LENGTH_SHORT).show()
            downloadedApkFile = null
            binding.btnCheckUpdate.text = "업데이트 확인"
            return
        }

        // 안드로이드 8.0 이상에서 알 수 없는 소스 설치 권한 검사
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, "업데이트를 위해 '출처를 알 수 없는 앱 설치' 권한을 허용해 주세요.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
            return
        }

        val launched = updateManager.installApk(apkFile)
        if (!launched) {
            Toast.makeText(this, "설치 화면 호출에 실패했습니다. 파일 권한을 확인해 주세요.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupLogListeners() {
        binding.btnViewLogs.setOnClickListener {
            showLogViewerDialog()
        }

        binding.btnShareLogs.setOnClickListener {
            uploadLogsToDrive()
        }
    }

    private fun showLogViewerDialog() {
        val scrollView = ScrollView(this).apply {
            setPadding(30, 20, 30, 20)
        }

        val tvLogs = TextView(this).apply {
            text = AppLogger.getRecentLogs(150)
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scrollView.addView(tvLogs)

        AlertDialog.Builder(this)
            .setTitle("📋 런타임 이벤트 및 진단 로그 (최근 150줄)")
            .setView(scrollView)
            .setPositiveButton("새로고침") { _, _ ->
                tvLogs.text = AppLogger.getRecentLogs(150)
                showLogViewerDialog()
            }
            .setNeutralButton("로그 비우기") { _, _ ->
                AppLogger.clearLogs()
                Toast.makeText(this, "로그가 초기화되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun uploadLogsToDrive() {
        val logFile = AppLogger.getLogFile()
        if (logFile == null || !logFile.exists() || logFile.length() == 0L) {
            Toast.makeText(this, "전송할 런타임 로그 기록이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnShareLogs.isEnabled = false
        binding.btnShareLogs.text = "전송 중..."
        Toast.makeText(this, "☁️ 구글 드라이브로 로그 전송 중...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val result = AppLogger.uploadLogsToGoogleDrive()
            binding.btnShareLogs.isEnabled = true
            binding.btnShareLogs.text = "☁️ 구글 드라이브로 로그 전송"

            result.onSuccess {
                Toast.makeText(this@MainActivity, "✅ 구글 드라이브 전송 완료! (스프레드시트 확인)", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, "전송 실패: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
