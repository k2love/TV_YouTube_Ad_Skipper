package com.example.tvadskipper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class TvAdSkipService : AccessibilityService() {

    companion object {
        private const val TAG = "TvAdSkipService"
        const val PREFS_NAME = "tv_ad_skipper_prefs"
        const val KEY_SKIP_COUNT = "key_skip_count"
        const val KEY_LAST_SKIP_TIME = "key_last_skip_time"
        const val KEY_PIXEL_SENSOR_ENABLED = "key_pixel_sensor_enabled"
        const val KEY_COBALT_CLICK_ENABLED = "key_cobalt_click_enabled"
        const val KEY_SHOW_TOAST = "key_show_toast"
        const val KEY_CUSTOM_X_PERCENT = "key_custom_x_percent"
        const val KEY_CUSTOM_Y_PERCENT = "key_custom_y_percent"
        const val KEY_SHORTS_AUTO_DISMISS = "key_shorts_auto_dismiss"
        const val KEY_QR_AD_AUTO_DISMISS = "key_qr_ad_auto_dismiss"

        private const val NORM_WIDTH = 480
        private const val NORM_HEIGHT = 270

        @Volatile
        var isServiceRunning = false
            private set

        @Volatile
        var instance: TvAdSkipService? = null
            private set
    }

    private var lastClickTime = 0L
    private val clickCooldownMs = 3500L
    private var lastQrDismissTime = 0L
    private val qrDismissCooldownMs = 2500L
    private var lastScreenshotTime = 0L
    private val minScreenshotIntervalMs = 1150L
    private val qrIdleScreenshotIntervalMs = 4500L
    private var lastEventProcessTime = 0L
    private val eventThrottleMs = 450L

    @Volatile
    private var lastAdUiSeenUptimeMs = 0L
    @Volatile
    private var lastYellowCountdownSeenUptimeMs = 0L

    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())
    private val screenshotExecutor = Executors.newSingleThreadExecutor()
    private val reusablePixelBuffer = IntArray(NORM_WIDTH * NORM_HEIGHT)

    @Volatile
    private var isAnalyzingScreenshot = false
    private val qrSentinelIntervalMs = 1350L

    @Volatile
    private var bootSkipCount = 0

    fun resetSkipCount() {
        bootSkipCount = 0
        if (::prefs.isInitialized) {
            prefs.edit().putInt(KEY_SKIP_COUNT, 0).apply()
        }
        AppLogger.i(TAG, "🔄 TvAdSkipService skip count reset to 0")
    }

    fun getSkipCount(): Int = bootSkipCount

    /**
     * 현재 화면에 광고 UI 또는 우하단 노란색 원형 카운트다운이 떠 있는지 즉시 반환 (IPC 호출 없음, 0ms)
     */
    fun isAdOrCountdownCurrentlyOnScreen(): Boolean {
        val now = SystemClock.uptimeMillis()
        return (now - lastAdUiSeenUptimeMs) < 2200L || (now - lastYellowCountdownSeenUptimeMs) < 2200L
    }

    /**
     * 현재 화면 우하단에 노란색 원형 카운트다운(강제 광고 진행 중)이 감지되고 있는지 반환
     */
    fun isYellowCountdownActive(): Boolean {
        val now = SystemClock.uptimeMillis()
        return (now - lastYellowCountdownSeenUptimeMs) < 2400L
    }

    private data class WindowScanResult(
        var isChatVisible: Boolean = false,
        var hasAccountKeyword: Boolean = false,
        var isAdUiVisible: Boolean = false,
        var isCountdownVisible: Boolean = false,
        var skipNode: AccessibilityNodeInfo? = null,
        var skipTriggerDesc: String = "",
        var qrAdDetected: Boolean = false,
        var qrCloseNode: AccessibilityNodeInfo? = null,
        var hasDisconnectPrompt: Boolean = false,
        var disconnectConfirmNode: AccessibilityNodeInfo? = null
    )

    private val visualSentinelRunnable = object : Runnable {
        override fun run() {
            if (!isServiceRunning) return
            try {
                triggerPeriodicVisualCheck()
            } catch (e: Exception) {
                Log.e(TAG, "Visual sentinel error", e)
            }
            if (isServiceRunning) {
                mainHandler.postDelayed(this, qrSentinelIntervalMs)
            }
        }
    }

    private fun startVisualSentinelLoop() {
        mainHandler.removeCallbacks(visualSentinelRunnable)
        mainHandler.postDelayed(visualSentinelRunnable, 1500L)
        Log.i(TAG, "👁️ Visual Sentinel loop started (interval: ${qrSentinelIntervalMs}ms)")
        AppLogger.i(TAG, "👁️ Visual Sentinel loop started (interval: ${qrSentinelIntervalMs}ms)")
    }

    private fun stopVisualSentinelLoop() {
        mainHandler.removeCallbacks(visualSentinelRunnable)
        Log.i(TAG, "Visual Sentinel loop stopped.")
    }

    private fun ensureLoungeServiceAlive() {
        try {
            val lounge = LoungeManager.getInstance(this)
            if (lounge.isPaired() && !LoungeSkipService.isServiceRunning) {
                AppLogger.i(TAG, "🔄 Restarting LoungeSkipService from TvAdSkipService watchdog")
                startService(Intent(this, LoungeSkipService::class.java))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to ensure LoungeSkipService alive", e)
        }
    }

    private fun triggerPeriodicVisualCheck() {
        val rootNode = rootInActiveWindow ?: return
        val activePkg = rootNode.packageName?.toString() ?: ""

        // ⚠️ Google TV 시스템 안정성 보호: 오직 공식 유튜브 TV 앱이 포그라운드일 때만 동작!
        val isYouTubeTv = activePkg == "com.google.android.youtube.tv" ||
                          activePkg == "com.google.android.youtube"
        if (!isYouTubeTv) return

        val currentTime = SystemClock.uptimeMillis()
        val displayMetrics: DisplayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // 단일 패스(Single-Pass) 노드 스캔으로 75회 이상 반복되던 IPC 호출을 1회로 압축하여 유튜브 버벅임 원천 제거
        val scan = WindowScanResult()
        scanNodeTreeSinglePass(rootNode, 0, screenWidth, screenHeight, scan, IntArray(1))

        if (scan.isAdUiVisible || scan.isCountdownVisible) {
            lastAdUiSeenUptimeMs = currentTime
            LoungeManager.getInstance(this).onAdDetectedFromLocalUi(scan.isCountdownVisible)
        } else {
            val lounge = LoungeManager.getInstance(this)
            if (lounge.isAutoMuted) {
                val noAdUiMs = currentTime - lastAdUiSeenUptimeMs
                val noYellowMs = currentTime - lastYellowCountdownSeenUptimeMs
                if (noAdUiMs >= 2000L && noYellowMs >= 2000L) {
                    lounge.onAdEndedFromLocalUi()
                }
            }
        }

        // 1. 스킵 버튼 노드가 명확히 발견되었고 카운트다운 중이 아니면 즉시 클릭
        if (scan.skipNode != null && !scan.isCountdownVisible && currentTime - lastClickTime >= clickCooldownMs) {
            if (tryPerformClick(scan.skipNode)) {
                onAdSkipped(currentTime, scan.skipTriggerDesc)
                return
            }
        }

        // 2. 좌측 하단 QR 쇼핑 광고 노드 처리
        val qrEnabled = prefs.getBoolean(KEY_QR_AD_AUTO_DISMISS, true)
        if (qrEnabled && scan.qrAdDetected && currentTime - lastQrDismissTime >= qrDismissCooldownMs) {
            if (scan.qrCloseNode != null && tryPerformClick(scan.qrCloseNode)) {
                onQrAdDismissed(currentTime, "SinglePass Close Node")
                return
            }
            dismissQrShoppingOverlay(screenWidth, screenHeight, currentTime, null, "SinglePass QR Keyword")
            return
        }

        // 3. 픽셀 센서: 광고 중일 때는 1.15초 주기로 정밀 감시, 일반 영상 시청 중에는 과부하 방지를 위해 4.5초 주기로만 경량 실행
        val pixelEnabled = prefs.getBoolean(KEY_PIXEL_SENSOR_ENABLED, true)
        if (pixelEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !scan.isChatVisible) {
            val isAdContext = LoungeManager.getInstance(this).isAdPlaying || scan.isAdUiVisible || scan.isCountdownVisible
            if (isAdContext) {
                analyzeScreenPixelSensor(currentTime, isAdContext = true, screenWidth, screenHeight)
            } else if (qrEnabled && currentTime - lastScreenshotTime >= qrIdleScreenshotIntervalMs) {
                analyzeScreenPixelSensor(currentTime, isAdContext = false, screenWidth, screenHeight)
            }
        }
    }

    private val qrShoppingKeywords = listOf(
        "수수료", "수수료 지급", "수수료지급", "QR 코드", "QR코드",
        "스캔하세요", "스캔하여", "스캔", "제품을 구매", "구매하기", "쇼핑", "상품"
    )

    private val qrCloseKeywords = listOf(
        "닫기", "광고 닫기", "Close", "Dismiss", "X", "x"
    )

    private val disconnectDialogKeywords = listOf(
        "연결을 해제", "연결해제", "연결 해제", "전송을 해제", "전송 해제",
        "전송을 중지", "전송 중지", "전송을 종료", "캐스팅을 중지", "Disconnect", "Stop casting"
    )

    private val specificDismissButtons = listOf(
        "전송 해제", "연결 해제", "전송 중지", "Stop casting", "Disconnect"
    )

    private val countdownRegex = Regex("""(\d+\s*초\s*(후|뒤)|영상이\s*곧\s*재생|동영상이\s*곧\s*재생|원본\s*영상이\s*곧|[Ss]kip\s+in\s+\d+|[Vv]ideo\s+will\s+play\s+after\s+ad)""")

    private val adBadgePattern = Regex("""^(광고(\s*[·•]?\s*\d+[/of\s]|[\s·•\u00A0]|$)|Ad(\s*[·•]?\s*\d+[/of\s]|[\s·•:]|$)|Sponsored)""", RegexOption.IGNORE_CASE)

    private val chatKeywords = listOf("채팅", "실시간 채팅", "Chat", "Live chat")

    private val accountKeywords = listOf("계정", "로그인", "로그아웃", "account", "sign in", "sign out", "비밀번호")

    private val knownSkipViewIds = setOf(
        "com.google.android.youtube.tv:id/skip_ad_button",
        "com.google.android.youtube:id/skip_ad_button",
        "com.google.android.youtube.tv:id/ad_skip_button",
        "com.google.android.youtube:id/ad_skip_button",
        "com.google.android.youtube.tv:id/skip_ad_button_container",
        "com.google.android.youtube:id/skip_ad_button_container"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppLogger.init(applicationContext)
        isServiceRunning = true
        instance = this
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        bootSkipCount = 0
        prefs.edit().putInt(KEY_SKIP_COUNT, 0).apply()
        Log.i(TAG, "TvAdSkipService connected. Boot skip count initialized to 0.")
        AppLogger.i(TAG, "TvAdSkipService connected. Boot skip count initialized to 0.")
        ensureLoungeServiceAlive()
        startVisualSentinelLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 1. IPC 호출(rootInActiveWindow) 전에 이벤트 패키지명 및 스로틀링부터 초고속 필터링하여 유튜브 UI 스레드 부하 차단!
        val pkgName = event.packageName?.toString() ?: ""
        if (pkgName == "com.example.tvadskipper" ||
            pkgName.contains("launcher", ignoreCase = true) ||
            pkgName.contains("settings", ignoreCase = true) ||
            pkgName.contains("setupwraith", ignoreCase = true) ||
            pkgName.contains("systemui", ignoreCase = true)
        ) {
            return
        }

        val currentTime = SystemClock.uptimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            currentTime - lastEventProcessTime < eventThrottleMs
        ) {
            return
        }

        val rootNode = rootInActiveWindow ?: return
        val activePkg = rootNode.packageName?.toString() ?: ""

        val isYouTubeContext = activePkg == "com.google.android.youtube.tv" ||
                               activePkg == "com.google.android.youtube" ||
                               pkgName == "com.google.android.youtube.tv" ||
                               pkgName == "com.google.android.youtube"
        if (!isYouTubeContext) return

        lastEventProcessTime = currentTime

        try {
            val displayMetrics: DisplayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val scan = WindowScanResult()
            scanNodeTreeSinglePass(rootNode, 0, screenWidth, screenHeight, scan, IntArray(1))

            // 광고 또는 카운트다운 문구가 보이는 즉시 0ms 로컬 하드웨어 음소거 발동!
            if (scan.isAdUiVisible || scan.isCountdownVisible) {
                lastAdUiSeenUptimeMs = currentTime
                LoungeManager.getInstance(this).onAdDetectedFromLocalUi(scan.isCountdownVisible)
            } else {
                val lounge = LoungeManager.getInstance(this)
                if (lounge.isAutoMuted) {
                    val noAdUiMs = currentTime - lastAdUiSeenUptimeMs
                    val noYellowMs = currentTime - lastYellowCountdownSeenUptimeMs
                    if (noAdUiMs >= 2000L && noYellowMs >= 2000L) {
                        lounge.onAdEndedFromLocalUi()
                    }
                }
            }

            // 쇼츠 연결 해제 팝업 자동 통과
            val shortsAutoDismiss = prefs.getBoolean(KEY_SHORTS_AUTO_DISMISS, false)
            if (shortsAutoDismiss && scan.hasDisconnectPrompt && !scan.hasAccountKeyword && !scan.isChatVisible) {
                val btn = scan.disconnectConfirmNode
                if (btn != null && tryPerformClick(btn)) {
                    lastClickTime = currentTime
                    AppLogger.i(TAG, "⚡ Successfully clicked disconnect confirmation button")
                    return
                }
            }

            // 좌측 하단 QR 쇼핑 오버레이 닫기
            val qrAdAutoDismiss = prefs.getBoolean(KEY_QR_AD_AUTO_DISMISS, true)
            if (qrAdAutoDismiss && scan.qrAdDetected && currentTime - lastQrDismissTime >= qrDismissCooldownMs) {
                val closeBtn = scan.qrCloseNode
                if (closeBtn != null && tryPerformClick(closeBtn)) {
                    onQrAdDismissed(currentTime, "Accessibility Close Node")
                    return
                }
                dismissQrShoppingOverlay(screenWidth, screenHeight, currentTime, null, "Accessibility Keyword Fallback")
                return
            }

            // 우하단 스킵 버튼 클릭
            if (scan.skipNode != null && !scan.isCountdownVisible && currentTime - lastClickTime >= clickCooldownMs) {
                if (tryPerformClick(scan.skipNode)) {
                    onAdSkipped(currentTime, scan.skipTriggerDesc)
                    return
                }
            }

            // ⚠️ 유튜브 버벅임 해결 핵심: 일반 영상 재생 중(isAdContext == false)에는 매 이벤트마다 스크린샷을 찍지 않음!
            val isAdContext = LoungeManager.getInstance(this).isAdPlaying || scan.isAdUiVisible || scan.isCountdownVisible
            if (!scan.isChatVisible && isAdContext) {
                val pixelSensorEnabled = prefs.getBoolean(KEY_PIXEL_SENSOR_ENABLED, true)
                if (pixelSensorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    analyzeScreenPixelSensor(currentTime, isAdContext = true, screenWidth, screenHeight)
                } else if (!scan.isCountdownVisible && currentTime - lastClickTime >= clickCooldownMs) {
                    val cobaltEnabled = prefs.getBoolean(KEY_COBALT_CLICK_ENABLED, false)
                    if (cobaltEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        performCobaltFallbackClick(currentTime)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    /**
     * 기존 75회 이상 반복되던 findAccessibilityNodeInfosByText IPC 호출을 단 1회의 트리 순회로 통합!
     */
    private fun scanNodeTreeSinglePass(
        node: AccessibilityNodeInfo?,
        depth: Int,
        screenWidth: Int,
        screenHeight: Int,
        out: WindowScanResult,
        visitedCount: IntArray
    ) {
        if (node == null || depth > 20 || visitedCount[0] > 90) return
        visitedCount[0]++

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val combined = if (desc.isEmpty()) text else if (text.isEmpty()) desc else "$text $desc"

        if (combined.isNotEmpty() || viewId.isNotEmpty()) {
            // 1. 채팅창 감지
            if (!out.isChatVisible && chatKeywords.any { combined.contains(it, ignoreCase = true) }) {
                out.isChatVisible = true
            }

            // 2. 계정/로그인 관련 문구 감지
            if (!out.hasAccountKeyword && accountKeywords.any { combined.contains(it, ignoreCase = true) }) {
                out.hasAccountKeyword = true
            }

            // 3. 카운트다운 진행 중 문구 ("5초 후 건너뛰기" 등) 감지
            val isCountdown = isCountdownText(text) || isCountdownText(desc)
            if (isCountdown) {
                out.isCountdownVisible = true
                out.isAdUiVisible = true
            }

            // 4. 실제 광고 배지 감지 (영상 제목/설명 등의 일반 단어 오탐 원천 차단)
            if (!out.isAdUiVisible) {
                if (isRealAdBadge(text, desc, viewId)) {
                    out.isAdUiVisible = true
                }
            }

            // 5. 스킵 버튼 노드 판별
            if (out.skipNode == null && !isCountdown && !isInvalidSkipText(text) && !isInvalidSkipText(desc)) {
                val isSkipViewId = knownSkipViewIds.contains(viewId) ||
                        viewId.contains("skip_ad", ignoreCase = true) ||
                        viewId.contains("ad_skip", ignoreCase = true)
                val isSkipText = text.equals("건너뛰기", ignoreCase = true) || desc.equals("건너뛰기", ignoreCase = true) ||
                        text.contains("광고 건너뛰기") || desc.contains("광고 건너뛰기") ||
                        text.contains("모두 건너뛰기") || desc.contains("모두 건너뛰기") ||
                        text.equals("Skip", ignoreCase = true) || desc.equals("Skip", ignoreCase = true) ||
                        text.startsWith("Skip Ad", ignoreCase = true) || desc.startsWith("Skip Ad", ignoreCase = true)
                if (isSkipViewId || isSkipText) {
                    out.skipNode = node
                    out.skipTriggerDesc = if (isSkipViewId) "ViewId: $viewId" else "Keyword: $combined"
                    out.isAdUiVisible = true
                }
            }

            // 6. 좌측 하단 QR 쇼핑 광고 및 닫기 버튼 판별
            if (!out.qrAdDetected && qrShoppingKeywords.any { combined.contains(it, ignoreCase = true) }) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.isEmpty || rect.centerX() < screenWidth * 0.6f) {
                    out.qrAdDetected = true
                }
            }
            if (out.qrCloseNode == null && qrCloseKeywords.any { combined.equals(it, ignoreCase = true) || combined.contains("광고 닫기") }) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.isEmpty || (rect.centerX() < screenWidth * 0.5f && rect.centerY() > screenHeight * 0.5f)) {
                    out.qrCloseNode = node
                }
            }

            // 7. 쇼츠 캐스팅 연결 해제 팝업 판별
            if (!out.hasDisconnectPrompt && disconnectDialogKeywords.any { combined.contains(it, ignoreCase = true) }) {
                out.hasDisconnectPrompt = true
            }
            if (out.disconnectConfirmNode == null && specificDismissButtons.any { combined.contains(it, ignoreCase = true) }) {
                out.disconnectConfirmNode = node
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            scanNodeTreeSinglePass(child, depth + 1, screenWidth, screenHeight, out, visitedCount)
        }
    }

    private fun dismissQrShoppingOverlay(
        width: Int,
        height: Int,
        currentTime: Long,
        dynamicTargetX: Float?,
        triggerInfo: String
    ) {
        lastQrDismissTime = currentTime
        lastClickTime = currentTime

        val targetX = dynamicTargetX ?: (width * 0.124f)
        val targetY = height * 0.893f

        Log.i(TAG, "🎯 [QR Shopping Ad] Dismissing overlay! ($triggerInfo) Target: ($targetX, $targetY)")
        AppLogger.i(TAG, "🎯 [QR Shopping Ad] Dismissing overlay! ($triggerInfo) Target: ($targetX, $targetY)")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mainHandler.post {
                clickAtCoordinates(targetX, targetY)
            }
            mainHandler.postDelayed({
                if (isServiceRunning) {
                    clickAtCoordinates(targetX, targetY)
                }
            }, 130L)
        }
    }

    private fun onQrAdDismissed(currentTime: Long, triggerInfo: String) {
        lastQrDismissTime = currentTime
        lastClickTime = currentTime
        Log.i(TAG, "🎯 Successfully dismissed QR Shopping Ad Overlay! ($triggerInfo)")
        AppLogger.i(TAG, "🎯 Successfully dismissed QR Shopping Ad Overlay! ($triggerInfo)")
    }

    private fun isInvalidSkipText(text: String): Boolean {
        if (text.isEmpty()) return false
        if (text.length > 25) return true
        if (text.contains("자동 실행") || text.contains("회)") || text.contains("완료") || text.contains("설정")) {
            return true
        }
        return false
    }

    private fun isCountdownText(text: String): Boolean {
        if (text.isEmpty() || text.length > 35) return false
        return countdownRegex.containsMatchIn(text)
    }

    private fun isRealAdBadge(text: String, desc: String, viewId: String): Boolean {
        if (knownSkipViewIds.contains(viewId) ||
            viewId.contains("skip_ad", ignoreCase = true) ||
            viewId.contains("ad_skip", ignoreCase = true) ||
            viewId.contains("ad_badge", ignoreCase = true) ||
            viewId.contains("ad_indicator", ignoreCase = true)
        ) {
            return true
        }

        for (raw in listOf(text, desc)) {
            val t = raw.trim()
            if (t.isNotEmpty() && t.length <= 20 && adBadgePattern.containsMatchIn(t)) {
                return true
            }
        }
        return false
    }

    private fun tryPerformClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        var executed = false
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 3) {
            try {
                if (current.isClickable) {
                    val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) {
                        Log.d(TAG, "Clicked node via ACTION_CLICK at depth $depth")
                        executed = true
                        break
                    }
                }
            } catch (_: Exception) {}
            current = current.parent
            depth++
        }

        if (!executed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val displayMetrics: DisplayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val rect = Rect()
            node.getBoundsInScreen(rect)

            val (targetX, targetY) = if (rect.width() > 10 && rect.height() > 10 &&
                rect.centerX() > screenWidth * 0.5f && rect.centerY() > screenHeight * 0.5f
            ) {
                Pair(rect.centerX().toFloat(), rect.centerY().toFloat())
            } else {
                Pair(
                    screenWidth * prefs.getFloat(KEY_CUSTOM_X_PERCENT, 0.895f),
                    screenHeight * prefs.getFloat(KEY_CUSTOM_Y_PERCENT, 0.895f)
                )
            }

            performDoubleTapSkipGesture(targetX, targetY)
            executed = true
        }

        return executed
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.N)
    private fun performDoubleTapSkipGesture(targetX: Float, targetY: Float) {
        mainHandler.post {
            clickAtCoordinates(targetX, targetY)
        }
        mainHandler.postDelayed({
            if (isServiceRunning) {
                clickAtCoordinates(targetX, targetY)
            }
        }, 130L)
    }

    /**
     * [초경량 백그라운드 컴퓨터 비전 픽셀 센서]:
     * 1. 메인 스레드(mainExecutor) 대신 전용 백그라운드 스레드(screenshotExecutor)에서 처리하여 유튜브 프레임 드랍 0% 달성!
     * 2. 4K(33MB)/1080p(8.3MB) 전체 버퍼를 그대로 복사하지 않고 즉시 480x270(0.5MB)으로 축소 후 1회 getPixels로 고속 스캔!
     * 3. 노란색 원형 카운트다운(yellowPixels >= 12)이 존재할 때는 절대 스킵 버튼을 클릭하지 않고 음소거만 유지!
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun analyzeScreenPixelSensor(
        currentTime: Long,
        isAdContext: Boolean,
        screenWidth: Int,
        screenHeight: Int
    ) {
        if (isAnalyzingScreenshot) return
        if (currentTime - lastScreenshotTime < minScreenshotIntervalMs) return

        isAnalyzingScreenshot = true
        lastScreenshotTime = currentTime

        takeScreenshot(Display.DEFAULT_DISPLAY, screenshotExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshotResult: ScreenshotResult) {
                try {
                    val hwBitmap = Bitmap.wrapHardwareBuffer(
                        screenshotResult.hardwareBuffer,
                        screenshotResult.colorSpace
                    )
                    screenshotResult.hardwareBuffer.close()

                    if (hwBitmap == null) {
                        isAnalyzingScreenshot = false
                        return
                    }

                    val scaled = Bitmap.createScaledBitmap(hwBitmap, NORM_WIDTH, NORM_HEIGHT, false)
                    if (scaled !== hwBitmap) {
                        hwBitmap.recycle()
                    }

                    val softBitmap = scaled.copy(Bitmap.Config.ARGB_8888, false)
                    scaled.recycle()

                    if (softBitmap == null) {
                        isAnalyzingScreenshot = false
                        return
                    }

                    softBitmap.getPixels(reusablePixelBuffer, 0, NORM_WIDTH, 0, 0, NORM_WIDTH, NORM_HEIGHT)
                    softBitmap.recycle()

                    inspectNormalizedPixelsAndTrigger(reusablePixelBuffer, currentTime, isAdContext, screenWidth, screenHeight)
                } catch (e: Exception) {
                    Log.e(TAG, "Screenshot inspection error", e)
                } finally {
                    isAnalyzingScreenshot = false
                }
            }

            override fun onFailure(errorCode: Int) {
                if (errorCode != 3) {
                    Log.w(TAG, "takeScreenshot failed: errorCode=$errorCode")
                }
                isAnalyzingScreenshot = false
            }
        })
    }

    private fun inspectNormalizedPixelsAndTrigger(
        pixels: IntArray,
        currentTime: Long,
        isAdContext: Boolean,
        screenWidth: Int,
        screenHeight: Int
    ) {
        // 우하단 스킵 버튼 및 노란 원형 카운터 탐색 ROI (가로 78~96%, 세로 78~94%)
        val startX = (NORM_WIDTH * 0.78f).toInt()
        val endX = (NORM_WIDTH * 0.96f).toInt()
        val startY = (NORM_HEIGHT * 0.78f).toInt()
        val endY = (NORM_HEIGHT * 0.94f).toInt()

        var whitePixels = 0
        var yellowPixels = 0
        var blackPixels = 0
        var sumWhiteX = 0L
        var sumWhiteY = 0L

        for (y in startY until endY) {
            val rowOffset = y * NORM_WIDTH
            for (x in startX until endX) {
                val pixel = pixels[rowOffset + x]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val maxCh = max(r, max(g, b))
                val minCh = min(r, min(g, b))

                if (r > 185 && g > 140 && b < 95 && (r - b) > 100 && (g - b) > 65) {
                    yellowPixels++
                } else if (r > 195 && g > 195 && b > 195 && (maxCh - minCh) <= 25) {
                    whitePixels++
                    sumWhiteX += x
                    sumWhiteY += y
                } else if (r < 75 && g < 75 && b < 75) {
                    blackPixels++
                }
            }
        }

        // 좌측 하단 QR 제휴 쇼핑 카드 정밀 ROI 샘플링 (X: 3~35%, Y: 42~85%)
        val qrAutoDismiss = prefs.getBoolean(KEY_QR_AD_AUTO_DISMISS, true)
        var qrWhitePixels = 0
        var qrDarkPixels = 0
        var sumQrWhiteX = 0L
        var qrTransitions = 0

        if (qrAutoDismiss && currentTime - lastQrDismissTime >= qrDismissCooldownMs) {
            val qrStartX = (NORM_WIDTH * 0.03f).toInt()
            val qrEndX = (NORM_WIDTH * 0.35f).toInt()
            val qrStartY = (NORM_HEIGHT * 0.42f).toInt()
            val qrEndY = (NORM_HEIGHT * 0.85f).toInt()

            for (qy in qrStartY until qrEndY) {
                var lastColorState = -1
                val rowOffset = qy * NORM_WIDTH
                for (qx in qrStartX until qrEndX) {
                    val pixel = pixels[rowOffset + qx]
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val maxCh = max(r, max(g, b))
                    val minCh = min(r, min(g, b))

                    if (r > 200 && g > 200 && b > 200 && (maxCh - minCh) <= 20) {
                        qrWhitePixels++
                        sumQrWhiteX += qx
                        if (lastColorState == 0) qrTransitions++
                        lastColorState = 1
                    } else if (r < 65 && g < 65 && b < 65) {
                        qrDarkPixels++
                        if (lastColorState == 1) qrTransitions++
                        lastColorState = 0
                    }
                }
            }
        }

        // 0. 좌측 하단 QR 제휴 쇼핑 카드 검출 시 하단 X 버튼 정밀 타격
        if (qrAutoDismiss && qrWhitePixels in 70..1800 && qrDarkPixels >= 15 && qrTransitions >= 20 &&
            currentTime - lastQrDismissTime >= qrDismissCooldownMs
        ) {
            val qrNormX = (sumQrWhiteX.toFloat() / qrWhitePixels) / NORM_WIDTH.toFloat()
            val dynamicX = if (qrNormX in 0.08f..0.28f) (screenWidth * qrNormX) else (screenWidth * 0.124f)
            dismissQrShoppingOverlay(
                screenWidth,
                screenHeight,
                currentTime,
                dynamicX,
                "Pixel Sensor (QR_W: $qrWhitePixels, QR_D: $qrDarkPixels, Trans: $qrTransitions)"
            )
            return
        }

        // ⚠️ 절대 원칙: 일반 영상 시청 중(!isAdContext)에는 우하단 픽셀 센서(노란 카운터/스킵 버튼) 검사를 전면 차단!
        // 영상 내부의 노란색 자막, 황혼/폭발/조명, 노란 옷/로고 등으로 인한 정상 영상 오음소거 완벽 방지
        if (!isAdContext) return

        // 1. 실제 광고 진행 중(isAdContext == true)일 때만 우하단 노란 원형 카운터 감지 및 음소거 유지
        if (yellowPixels >= 18) {
            val nowUptime = SystemClock.uptimeMillis()
            lastYellowCountdownSeenUptimeMs = nowUptime
            lastAdUiSeenUptimeMs = nowUptime
            LoungeManager.getInstance(this).onAdDetectedFromLocalUi(isCountdownActive = true)
            return
        }
        if (currentTime - lastClickTime < clickCooldownMs) return

        // 2. 노란색 카운터가 완전히 사라진 상태(yellowPixels < 12)에서 적정 크기의 흰색 알약 스킵 버튼(55~650px)과 글자(blackPixels >= 6) 검출 시에만 스킵!
        // (whitePixels <= 650 상한선을 두어 광고 영상 전체가 흰색 배경일 때 스킵 버튼으로 오판하던 3~4초 볼륨 폭발 버그 차단)
        val isSkipButtonDetected = whitePixels in 55..650 && blackPixels in 6..200 && yellowPixels < 12

        if (isSkipButtonDetected) {
            val normX = (sumWhiteX.toFloat() / whitePixels) / NORM_WIDTH.toFloat()
            val normY = (sumWhiteY.toFloat() / whitePixels) / NORM_HEIGHT.toFloat()

            val targetX = if (normX in 0.78f..0.96f) {
                screenWidth * normX
            } else {
                screenWidth * prefs.getFloat(KEY_CUSTOM_X_PERCENT, 0.895f)
            }
            val targetY = if (normY in 0.78f..0.94f) {
                screenHeight * normY
            } else {
                screenHeight * prefs.getFloat(KEY_CUSTOM_Y_PERCENT, 0.895f)
            }

            AppLogger.i(TAG, "👁️ [Pixel Sensor] Skip Button confirmed (W:$whitePixels, B:$blackPixels, Y:$yellowPixels)! Clicking (${targetX.toInt()}, ${targetY.toInt()})")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                performDoubleTapSkipGesture(targetX, targetY)
                onAdSkipped(currentTime, "Pixel Sensor (${targetX.toInt()}, ${targetY.toInt()})")
            }
        }
    }

    private fun performCobaltFallbackClick(currentTime: Long) {
        val displayMetrics: DisplayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val targetX = width * prefs.getFloat(KEY_CUSTOM_X_PERCENT, 0.895f)
        val targetY = height * prefs.getFloat(KEY_CUSTOM_Y_PERCENT, 0.895f)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            performDoubleTapSkipGesture(targetX, targetY)
            onAdSkipped(currentTime, "Cobalt Fallback")
        }
    }

    /**
     * 카운트다운 타이머 등에서 호출 시, 화면을 무조건 터치하지 않고 실제 스킵 노드가 보일 때만 안전하게 클릭
     */
    fun tryClickVisibleSkipNodeOnly(): Boolean {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastClickTime < clickCooldownMs) return false
        val rootNode = rootInActiveWindow ?: return false
        val displayMetrics: DisplayMetrics = resources.displayMetrics
        val scan = WindowScanResult()
        scanNodeTreeSinglePass(rootNode, 0, displayMetrics.widthPixels, displayMetrics.heightPixels, scan, IntArray(1))
        if (scan.skipNode != null && !scan.isCountdownVisible) {
            if (tryPerformClick(scan.skipNode)) {
                onAdSkipped(currentTime, "Verified Skip Node (${scan.skipTriggerDesc})")
                return true
            }
        }
        return false
    }

    /**
     * Lounge에서 명시적으로 isSkipEnabled == "true" 신호를 받았을 때 호출
     */
    fun triggerDirectScreenSkip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastClickTime < clickCooldownMs) return

        if (tryClickVisibleSkipNodeOnly()) return

        val displayMetrics: DisplayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val targetX = width * prefs.getFloat(KEY_CUSTOM_X_PERCENT, 0.895f)
        val targetY = height * prefs.getFloat(KEY_CUSTOM_Y_PERCENT, 0.895f)

        AppLogger.i(TAG, "⚡ [Hybrid-Skip] Triggering 2-step direct screen click from isSkipEnabled=true at ($targetX, $targetY)")
        performDoubleTapSkipGesture(targetX, targetY)
        onAdSkipped(currentTime, "Hybrid isSkipEnabled Signal")
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.N)
    private fun clickAtCoordinates(x: Float, y: Float) {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(clickPath, 0L, 90L))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Gesture completed successfully at ($x, $y)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Gesture cancelled at ($x, $y)")
            }
        }, null)
    }

    private fun onAdSkipped(currentTime: Long, triggerInfo: String) {
        lastClickTime = currentTime
        lastAdUiSeenUptimeMs = 0L
        lastYellowCountdownSeenUptimeMs = 0L
        Log.i(TAG, "Successfully skipped YouTube Ad! ($triggerInfo)")
        AppLogger.i(TAG, "🎯 [Accessibility] Successfully skipped YouTube Ad! ($triggerInfo)")

        // ⚠️ 중요: 기존에는 여기서 forceRestoreAudio()를 호출하여 강제 광고 도중 오클릭이나
        // 1/2 광고 스킵 직후에도 무조건 시스템 볼륨을 10(최대)으로 올려버리는 치명적 버그가 있었음!
        // 이제는 onAccessibilitySkipClicked()를 통해 실제 광고 종료 확인 후에만 볼륨을 복원함.
        try {
            LoungeManager.getInstance(this).onAccessibilitySkipClicked()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to notify skip to LoungeManager", e)
        }

        if (::prefs.isInitialized) {
            bootSkipCount++
            prefs.edit()
                .putInt(KEY_SKIP_COUNT, bootSkipCount)
                .putLong(KEY_LAST_SKIP_TIME, System.currentTimeMillis())
                .apply()

            Log.i(TAG, "⚡ Ad skipped successfully (boot count: $bootSkipCount)")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "TvAdSkipService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVisualSentinelLoop()
        isServiceRunning = false
        instance = null
    }
}
