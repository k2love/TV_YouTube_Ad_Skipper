package com.example.tvadskipper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
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

class TvAdSkipService : AccessibilityService() {

    companion object {
        private const val TAG = "TvAdSkipService"
        const val PREFS_NAME = "ad_skipper_prefs"
        const val KEY_CUSTOM_X_PERCENT = "custom_x_percent"
        const val KEY_CUSTOM_Y_PERCENT = "custom_y_percent"
        const val KEY_SKIP_COUNT = "skip_count"
        const val KEY_LAST_SKIP_TIME = "last_skip_time"
        const val KEY_SHORTS_AUTO_DISMISS = "shorts_auto_dismiss"
        const val KEY_PIXEL_SENSOR_ENABLED = "pixel_sensor_enabled"
        const val KEY_QR_AD_AUTO_DISMISS = "qr_ad_auto_dismiss"

        // 픽셀 센서 정규화 해상도
        private const val NORM_WIDTH = 320
        private const val NORM_HEIGHT = 180

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
    private val minScreenshotIntervalMs = 1200L
    private var lastEventProcessTime = 0L
    private val eventThrottleMs = 800L

    @Volatile
    private var lastAdUiSeenUptimeMs = 0L
    @Volatile
    private var lastYellowCountdownSeenUptimeMs = 0L
    @Volatile
    private var isAdWatchActive = false
    @Volatile
    private var isDormantMode = false

    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())
    private val screenshotExecutor = Executors.newSingleThreadExecutor()
    private val reusablePixelBuffer = IntArray(NORM_WIDTH * NORM_HEIGHT)

    @Volatile
    private var isAnalyzingScreenshot = false
    private val adSentinelIntervalMs = 1250L

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
        return (now - lastAdUiSeenUptimeMs) < 2500L || (now - lastYellowCountdownSeenUptimeMs) < 2500L
    }

    /**
     * 현재 화면 우하단에 노란색 원형 카운트다운(강제 광고 진행 중)이 감지되고 있는지 반환
     */
    fun isYellowCountdownActive(): Boolean {
        val now = SystemClock.uptimeMillis()
        return (now - lastYellowCountdownSeenUptimeMs) < 2500L
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

    /**
     * ⚠️ [방향 1+2 핵심]: 본 영상 시청 중에는 이 루프가 절대 돌지 않음!
     * 오직 LoungeManager가 광고 시작(isAdPlaying == true)을 알렸을 때만 광고 시간(5~30초) 동안 일시 가동되고,
     * 본 영상이 시작되는 즉시 stopAdWatchLoop()로 100% 정지됨 (4K 60fps 재생 중 CPU/GPU/IPC 부하 0.0% 보장!)
     */
    private val adSentinelRunnable = object : Runnable {
        override fun run() {
            if (!isServiceRunning || !isAdWatchActive) return
            try {
                if (!LoungeManager.getInstance(this@TvAdSkipService).isAdPlaying &&
                    !isAdOrCountdownCurrentlyOnScreen()
                ) {
                    onAdEndedSleep()
                    return
                }
                triggerAdOnlyCheck()
            } catch (e: Exception) {
                Log.e(TAG, "Ad sentinel error", e)
            }
            if (isServiceRunning && isAdWatchActive) {
                mainHandler.postDelayed(this, adSentinelIntervalMs)
            }
        }
    }

    /**
     * LoungeManager가 광고 시작을 감지했을 때 호출 -> 광고 구간 동안만 센티넬 감시 깨우기(Wake-up)
     */
    fun onAdStartedWakeUp() {
        mainHandler.post {
            if (!isServiceRunning) return@post
            setDynamicDormantState(dormant = false)
            if (!isAdWatchActive) {
                isAdWatchActive = true
                mainHandler.removeCallbacks(adSentinelRunnable)
                mainHandler.postDelayed(adSentinelRunnable, 600L)
                AppLogger.i(TAG, "⚡ [Wake-Up] Ad started -> Waking up TvAdSkipService sentinel for ad duration only.")
            }
        }
    }

    /**
     * LoungeManager가 광고 종료(본 영상 재생)를 확정했을 때 호출 -> 즉시 완전 동면(Deep Sleep) 진입!
     * 본 영상 시청 중 폴링 0회, 스크린샷 0회, 접근성 이벤트 수신 0회로 4K 60fps 끊김을 물리적으로 100% 차단!
     */
    fun onAdEndedSleep() {
        mainHandler.post {
            isAdWatchActive = false
            mainHandler.removeCallbacks(adSentinelRunnable)
            if (LoungeManager.getInstance(this).isConnected) {
                setDynamicDormantState(dormant = true)
            }
            AppLogger.i(TAG, "💤 [Deep-Sleep] Main video playing -> TvAdSkipService entered 0% CPU/GPU dormant mode.")
        }
    }

    /**
     * 런타임 동적 AccessibilityServiceInfo 제어:
     * 본 영상 재생 중(dormant = true)에는 eventTypes = 0으로 비워 안드로이드 시스템과 유튜브(Cobalt)가
     * 접근성 이벤트를 아예 생성/직렬화하지 않도록 원천 차단!
     */
    private fun setDynamicDormantState(dormant: Boolean) {
        if (isDormantMode == dormant) return
        try {
            val info = serviceInfo ?: return
            if (dormant) {
                info.eventTypes = 0
                info.flags = 0
                info.notificationTimeout = 1000L
            } else {
                info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                info.notificationTimeout = 500L
            }
            serviceInfo = info
            isDormantMode = dormant
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update dynamic serviceInfo", e)
        }
    }

    /**
     * [비픽셀 QR 쇼핑 광고 제어]:
     * 상시 캡처 대신, LoungeManager가 새 영상 진입 시 YouTube Innertube API 메타데이터에서
     * 실제 쇼핑/제휴 상품 태그(merchandise/shopping)가 있음을 확인한 영상에서만 본 영상 시작 직후 단발성으로 호출!
     */
    fun performTargetedQrDismissFromMetadata(videoId: String) {
        if (!prefs.getBoolean(KEY_QR_AD_AUTO_DISMISS, true)) return
        mainHandler.post {
            if (!isServiceRunning || LoungeManager.getInstance(this).isAdPlaying) return@post
            val currentTime = SystemClock.uptimeMillis()
            if (currentTime - lastQrDismissTime < qrDismissCooldownMs) return@post

            val displayMetrics: DisplayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            // 1차: 접근성 노드 트리에 닫기 버튼이나 QR 텍스트가 노출되어 있으면 즉시 클릭 (캡처 0회)
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val scan = WindowScanResult()
                scanNodeTreeSinglePass(rootNode, 0, screenWidth, screenHeight, scan, IntArray(1))
                if (scan.qrCloseNode != null && tryPerformClick(scan.qrCloseNode)) {
                    onQrAdDismissed(currentTime, "Innertube Metadata + Close Node ($videoId)")
                    return@post
                }
                if (scan.qrAdDetected) {
                    dismissQrShoppingOverlay(screenWidth, screenHeight, currentTime, null, "Innertube Metadata + Node Keyword ($videoId)")
                    return@post
                }
            }

            // 2차: Innertube 메타데이터로 쇼핑 상품 영상임이 확정된 상태에서, 단 1회만 초경량 확인 후 닫기 실행
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                analyzeSingleShotQrOnly(currentTime, screenWidth, screenHeight, videoId)
            } else {
                dismissQrShoppingOverlay(screenWidth, screenHeight, currentTime, null, "Innertube Metadata Direct ($videoId)")
            }
        }
    }

    /**
     * 오직 광고가 재생 중일 때(isAdWatchActive == true)만 실행되는 광고 전용 감시 루틴
     */
    private fun triggerAdOnlyCheck() {
        val rootNode = rootInActiveWindow ?: return
        val activePkg = rootNode.packageName?.toString() ?: ""

        val isYouTubeTv = activePkg == "com.google.android.youtube.tv" ||
                          activePkg == "com.google.android.youtube"
        if (!isYouTubeTv) return

        val currentTime = SystemClock.uptimeMillis()
        val displayMetrics: DisplayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val scan = WindowScanResult()
        scanNodeTreeSinglePass(rootNode, 0, screenWidth, screenHeight, scan, IntArray(1))

        if (scan.isAdUiVisible || scan.isCountdownVisible) {
            lastAdUiSeenUptimeMs = currentTime
            LoungeManager.getInstance(this).onAdDetectedFromLocalUi(scan.isCountdownVisible)
        }

        // 1. 스킵 버튼 노드가 명확히 발견되었고 카운트다운 중이 아니면 즉시 클릭
        if (scan.skipNode != null && !scan.isCountdownVisible && currentTime - lastClickTime >= clickCooldownMs) {
            if (tryPerformClick(scan.skipNode)) {
                onAdSkipped(currentTime, scan.skipTriggerDesc)
                return
            }
        }

        // 2. 픽셀 센서: 오직 광고가 실제로 재생 중이고(isAdContext == true) 스킵이 아직 안 되었을 때만 실행!
        val pixelEnabled = prefs.getBoolean(KEY_PIXEL_SENSOR_ENABLED, true)
        if (pixelEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !scan.isChatVisible) {
            val isAdContext = LoungeManager.getInstance(this).isAdPlaying || scan.isAdUiVisible || scan.isCountdownVisible
            if (isAdContext) {
                analyzeScreenPixelSensor(currentTime, screenWidth, screenHeight)
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

    /**
     * 13개 글로벌 언어 스킵 버튼 키워드 (한국어 최우선)
     */
    private val skipKeywords = listOf(
        // 한국어 (최우선)
        "건너뛰기", "광고 건너뛰기", "광고 건너뛰기 버튼", "광고 모두 건너뛰기", "모두 건너뛰기",
        // 영어
        "Skip", "Skip Ad", "Skip Ads", "Skip ad", "Skip ads",
        // 일본어
        "スキップ", "広告をスキップ", "すべてスキップ",
        // 중국어 (간체/번체)
        "跳过", "跳过广告", "略過", "略過廣告",
        // 스페인어
        "Saltar", "Saltar anuncio", "Saltar anuncios",
        // 포르투갈어
        "Pular", "Pular anúncio", "Pular anúncios",
        // 프랑스어
        "Passer", "Passer l'annonce", "Passer les annonces",
        // 독일어
        "Überspringen", "Werbung überspringen",
        // 러시아어
        "Пропустить", "Пропустить рекламу",
        // 인도네시아어
        "Lewati", "Lewati Iklan", "Lewati iklan",
        // 힌디어
        "छोड़ें", "विज्ञापन छोड़ें",
        // 아랍어
        "تخطي", "تخطي الإعلان",
        // 베트남어
        "Bỏ qua", "Bỏ qua quảng cáo"
    )

    /**
     * 13개 글로벌 언어 카운트다운 정규식
     */
    private val countdownRegex = Regex(
        """(\d+\s*초\s*(후|뒤|영상이\s*곧\s*재생|본영상이\s*곧\s*재생|재생됩니다)|""" +
        """[Ss]kip\s+(in|after)\s+\d+|[Vv]ideo\s+(will\s+)?play\s+after|[Pp]layback\s+will\s+begin|""" +
        """\d+\s*秒[後后]|動画が再生|将在|將在|""" +
        """[Ss]altar\s+en|[Pp]ular\s+em|[Pp]asser\s+dans|[Ww]erbung\s+endet\s+in|""" +
        """через\s+\d+|[Dd]alam\s+\d+|\d+\s*सेकंड|في\s+خلال|sau\s+\d+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 13개 글로벌 언어 광고 배지 감지 정규식
     */
    private val adBadgePattern = Regex(
        """^(광고|広告|广告|廣告|Ad|Ads|Sponsored|Anuncio|Anúncio|Annonce|Werbung|Реклама|Iklan|विज्ञापन|إعلان|Quảng cáo)(\s*[·•]?\s*\d+[/of\s]|[\s·•\u00A0]|$)""",
        RegexOption.IGNORE_CASE
    )

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
        Log.i(TAG, "TvAdSkipService connected (Ultra-Light Dormant Architecture v0.8.1).")
        AppLogger.i(TAG, "TvAdSkipService connected (Ultra-Light Dormant Architecture v0.8.1).")
        ensureLoungeServiceAlive()
        // ⚠️ 부팅 시 상시 폴링 루프를 돌리지 않고 동면(대기) 상태로 시작하여 TV 성능 100% 보존!
    }

    private fun ensureLoungeServiceAlive() {
        try {
            val lounge = LoungeManager.getInstance(this)
            if (lounge.isPaired() && !LoungeSkipService.isServiceRunning) {
                AppLogger.i(TAG, "🔄 Starting LoungeSkipService from TvAdSkipService")
                startService(Intent(this, LoungeSkipService::class.java))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to ensure LoungeSkipService alive", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Lounge가 연결되어 있고 본 영상 시청 중(광고가 아닐 때)에는 접근성 이벤트 처리를 즉시 반환하여 부하 0% 유지!
        val lounge = LoungeManager.getInstance(this)
        if (lounge.isConnected && !lounge.isAdPlaying && !isAdWatchActive) {
            return
        }

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
        if (currentTime - lastEventProcessTime < eventThrottleMs) {
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

            if (scan.isAdUiVisible || scan.isCountdownVisible) {
                lastAdUiSeenUptimeMs = currentTime
                lounge.onAdDetectedFromLocalUi(scan.isCountdownVisible)
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

            // 좌측 하단 QR 쇼핑 오버레이 닫기 (접근성 노드로 노출된 경우 캡처 없이 즉시 처리)
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
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    private fun scanNodeTreeSinglePass(
        node: AccessibilityNodeInfo?,
        depth: Int,
        screenWidth: Int,
        screenHeight: Int,
        out: WindowScanResult,
        visitedCount: IntArray
    ) {
        if (node == null || depth > 18 || visitedCount[0] > 70) return
        visitedCount[0]++

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val combined = if (desc.isEmpty()) text else if (text.isEmpty()) desc else "$text $desc"

        if (combined.isNotEmpty() || viewId.isNotEmpty()) {
            if (!out.isChatVisible && chatKeywords.any { combined.contains(it, ignoreCase = true) }) {
                out.isChatVisible = true
            }

            if (!out.hasAccountKeyword && accountKeywords.any { combined.contains(it, ignoreCase = true) }) {
                out.hasAccountKeyword = true
            }

            val isCountdown = isCountdownText(text) || isCountdownText(desc)
            if (isCountdown) {
                out.isCountdownVisible = true
                out.isAdUiVisible = true
            }

            // 실제 광고 배지 감지 (영상 제목/설명 등의 일반 단어 오탐 원천 차단)
            if (!out.isAdUiVisible) {
                if (isRealAdBadge(text, desc, viewId)) {
                    out.isAdUiVisible = true
                }
            }

            // 스킵 버튼 노드 식별
            if (out.skipNode == null && !isCountdown && !isInvalidSkipText(text) && !isInvalidSkipText(desc)) {
                val isSkipViewId = knownSkipViewIds.contains(viewId) ||
                        viewId.contains("skip_ad", ignoreCase = true) ||
                        viewId.contains("ad_skip", ignoreCase = true)
                val isSkipText = skipKeywords.any { kw ->
                    text.contains(kw, ignoreCase = true) || desc.contains(kw, ignoreCase = true)
                }
                if (isSkipViewId || isSkipText) {
                    out.skipNode = node
                    out.skipTriggerDesc = if (isSkipViewId) "ViewId: $viewId" else "Keyword: $combined"
                    out.isAdUiVisible = true
                }
            }

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
            viewId.contains("ad_text", ignoreCase = true)
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
     * Innertube API에서 쇼핑/제휴 상품 태그가 확인된 영상에서만 본 영상 시작 직후 단 1회 실행하는 원샷(One-Shot) 검증
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun analyzeSingleShotQrOnly(
        currentTime: Long,
        screenWidth: Int,
        screenHeight: Int,
        videoId: String
    ) {
        if (isAnalyzingScreenshot) return
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

                    val softwareBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    hwBitmap.recycle()

                    val scaledBitmap = Bitmap.createScaledBitmap(
                        softwareBitmap,
                        NORM_WIDTH,
                        NORM_HEIGHT,
                        true
                    )
                    softwareBitmap.recycle()

                    scaledBitmap.getPixels(
                        reusablePixelBuffer,
                        0,
                        NORM_WIDTH,
                        0,
                        0,
                        NORM_WIDTH,
                        NORM_HEIGHT
                    )
                    scaledBitmap.recycle()

                    // 좌하단 QR 코드 및 팝업 박스 검증
                    val qrMinX = 10
                    val qrMaxX = (NORM_WIDTH * 0.35f).toInt()
                    val qrMinY = (NORM_HEIGHT * 0.65f).toInt()
                    val qrMaxY = NORM_HEIGHT - 6

                    var qrWhiteHigh = 0
                    var qrDarkLow = 0
                    var qrBoxFound = false
                    var detectedCloseX: Float? = null

                    for (y in qrMinY..qrMaxY) {
                        for (x in qrMinX..qrMaxX) {
                            val pixel = reusablePixelBuffer[y * NORM_WIDTH + x]
                            val r = Color.red(pixel)
                            val g = Color.green(pixel)
                            val b = Color.blue(pixel)

                            if (r >= 225 && g >= 225 && b >= 225) {
                                qrWhiteHigh++
                            } else if (r <= 35 && g <= 35 && b <= 35) {
                                qrDarkLow++
                            }

                            if (y in (qrMinY + 5)..(qrMaxY - 15) && x in (qrMinX + 5)..(qrMaxX - 35)) {
                                if (r in 18..35 && g in 18..35 && b in 18..35) {
                                    val rightPixel = reusablePixelBuffer[y * NORM_WIDTH + (x + 30)]
                                    val rr = Color.red(rightPixel)
                                    val rg = Color.green(rightPixel)
                                    val rb = Color.blue(rightPixel)
                                    if (rr in 18..35 && rg in 18..35 && rb in 18..35) {
                                        qrBoxFound = true
                                        val normDetectedX = x.toFloat() / NORM_WIDTH.toFloat()
                                        if (normDetectedX in 0.05f..0.25f) {
                                            detectedCloseX = screenWidth * normDetectedX
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val isQrPatternPresent = qrWhiteHigh in 35..1200 && qrDarkLow in 35..1200
                    if (isQrPatternPresent || qrBoxFound) {
                        AppLogger.i(TAG, "🛒 [Targeted QR Check] QR overlay confirmed on screen ($videoId)! White:$qrWhiteHigh, Dark:$qrDarkLow, Box:$qrBoxFound")
                        mainHandler.post {
                            dismissQrShoppingOverlay(
                                screenWidth,
                                screenHeight,
                                currentTime,
                                detectedCloseX,
                                "Innertube Targeted Match ($videoId)"
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in analyzeSingleShotQrOnly", e)
                } finally {
                    isAnalyzingScreenshot = false
                }
            }

            override fun onFailure(errorCode: Int) {
                isAnalyzingScreenshot = false
                Log.w(TAG, "Single shot screenshot failed with code $errorCode")
            }
        })
    }

    /**
     * 오직 광고가 실제로 재생 중일 때만 스킵 버튼 및 노란 카운터를 보조 감지하는 픽셀 센서
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun analyzeScreenPixelSensor(currentTime: Long, screenWidth: Int, screenHeight: Int) {
        if (currentTime - lastScreenshotTime < minScreenshotIntervalMs) return
        if (isAnalyzingScreenshot) return
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

                    val softwareBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    hwBitmap.recycle()

                    val scaledBitmap = Bitmap.createScaledBitmap(
                        softwareBitmap,
                        NORM_WIDTH,
                        NORM_HEIGHT,
                        true
                    )
                    softwareBitmap.recycle()

                    scaledBitmap.getPixels(
                        reusablePixelBuffer,
                        0,
                        NORM_WIDTH,
                        0,
                        0,
                        NORM_WIDTH,
                        NORM_HEIGHT
                    )
                    scaledBitmap.recycle()

                    processPixelBuffer(currentTime, screenWidth, screenHeight)
                } catch (e: Exception) {
                    Log.e(TAG, "Error analyzing pixel buffer", e)
                } finally {
                    isAnalyzingScreenshot = false
                }
            }

            override fun onFailure(errorCode: Int) {
                isAnalyzingScreenshot = false
                Log.w(TAG, "Screenshot failed with error code: $errorCode")
            }
        })
    }

    private fun processPixelBuffer(currentTime: Long, screenWidth: Int, screenHeight: Int) {
        var whitePixels = 0
        var blackPixels = 0
        var yellowPixels = 0
        var sumWhiteX = 0L
        var sumWhiteY = 0L

        val scanMinX = (NORM_WIDTH * 0.72f).toInt()
        val scanMaxX = (NORM_WIDTH * 0.98f).toInt()
        val scanMinY = (NORM_HEIGHT * 0.72f).toInt()
        val scanMaxY = (NORM_HEIGHT * 0.98f).toInt()

        for (y in scanMinY..scanMaxY) {
            for (x in scanMinX..scanMaxX) {
                val pixel = reusablePixelBuffer[y * NORM_WIDTH + x]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                if (r > 200 && g in 130..220 && b < 65) {
                    yellowPixels++
                }

                if (r >= 240 && g >= 240 && b >= 240) {
                    whitePixels++
                    sumWhiteX += x
                    sumWhiteY += y
                } else if (r <= 25 && g <= 25 && b <= 25) {
                    blackPixels++
                }
            }
        }

        // 1. 노란색 카운터(원형 타이머 또는 링) 감지 시
        if (yellowPixels >= 12) {
            val nowUptime = SystemClock.uptimeMillis()
            lastYellowCountdownSeenUptimeMs = nowUptime
            lastAdUiSeenUptimeMs = nowUptime
            LoungeManager.getInstance(this).onAdDetectedFromLocalUi(isCountdownActive = true)
            return
        }
        if (currentTime - lastClickTime < clickCooldownMs) return

        // 2. 노란색 카운터가 완전히 사라진 상태에서 적정 크기의 흰색 알약 스킵 버튼과 글자 검출 시에만 스킵
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
        mainHandler.removeCallbacks(adSentinelRunnable)
        isServiceRunning = false
        instance = null
    }
}
