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

        @Volatile
        var isServiceRunning = false
            private set

        @Volatile
        var instance: TvAdSkipService? = null
            private set
    }

    private var lastClickTime = 0L
    private val clickCooldownMs = 4000L
    private var lastQrDismissTime = 0L
    private val qrDismissCooldownMs = 2500L
    private var lastScreenshotTime = 0L
    private val minScreenshotIntervalMs = 1150L
    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isAnalyzingScreenshot = false
    private val qrSentinelIntervalMs = 1200L

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

    /**
     * TV 부팅 직후나 백그라운드 메모리 회수 후에도 LoungeSkipService가 항상 살아있도록 보장
     */
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
        val rootNode = rootInActiveWindow
        val activePkg = rootNode?.packageName?.toString() ?: ""

        // ⚠️ Google TV 시스템 안정성 보호: 오직 공식 유튜브 TV 앱이 포그라운드일 때만 동작!
        // 런처, 시스템 UI("android"), 설정 화면 등에서는 1바이트의 리소스도 쓰지 않고 즉시 반환
        val isYouTubeTv = activePkg == "com.google.android.youtube.tv" ||
                          activePkg == "com.google.android.youtube"
        if (!isYouTubeTv) return

        val qrEnabled = prefs.getBoolean(KEY_QR_AD_AUTO_DISMISS, true)
        val pixelEnabled = prefs.getBoolean(KEY_PIXEL_SENSOR_ENABLED, true)
        val currentTime = SystemClock.uptimeMillis()

        // 1. 센티넬 루프에서 우하단 광고 건너뛰기 접근성 노드 탐색
        if (currentTime - lastClickTime >= clickCooldownMs) {
            var skippedByNode = false
            if (processNodeTree(rootNode, currentTime)) {
                skippedByNode = true
            }
            if (!skippedByNode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (window in windows) {
                    val wRoot = window.root ?: continue
                    if (processNodeTree(wRoot, currentTime)) {
                        skippedByNode = true
                        break
                    }
                }
            }
            if (skippedByNode) return
        }

        // 2. 좌측 하단 QR 쇼핑 광고 노드 탐색
        if (qrEnabled && currentTime - lastQrDismissTime >= qrDismissCooldownMs) {
            if (checkAndDismissQrAdOverlay(rootNode, currentTime)) {
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (window in windows) {
                    val wRoot = window.root ?: continue
                    if (checkAndDismissQrAdOverlay(wRoot, currentTime)) {
                        return
                    }
                }
            }
        }

        // 3. 픽셀 센서: ⚠️ 시스템 과부하 및 프레임 드랍 방지를 위해 실제 광고 컨텍스트일 때만 실행!
        if (pixelEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val isAdContext = isCurrentScreenInAdContext(rootNode)
            if (isAdContext && currentTime - lastClickTime >= clickCooldownMs) {
                val isChatVisible = isChatUiVisible(rootNode)
                if (!isChatVisible) {
                    analyzeScreenPixelSensor(currentTime, isAdContext)
                }
            }
        }
    }

    private val qrShoppingKeywords = listOf(
        "수수료",
        "수수료 지급",
        "수수료지급",
        "QR 코드",
        "QR코드",
        "스캔하세요",
        "스캔하여",
        "스캔",
        "제품을 구매",
        "구매하기",
        "쇼핑",
        "상품"
    )

    private val qrCloseKeywords = listOf(
        "닫기",
        "광고 닫기",
        "Close",
        "Dismiss",
        "X",
        "x"
    )

    private val disconnectDialogKeywords = listOf(
        "연결을 해제",
        "연결해제",
        "연결 해제",
        "전송을 해제",
        "전송 해제",
        "전송을 중지",
        "전송 중지",
        "전송을 종료",
        "캐스팅을 중지",
        "Disconnect",
        "Stop casting"
    )

    private val dismissButtonKeywords = listOf(
        "해제",
        "연결 해제",
        "연결해제",
        "전송 해제",
        "전송 중지",
        "중지",
        "확인",
        "예",
        "Disconnect",
        "Stop",
        "OK"
    )

    private val skipKeywords = listOf(
        "건너뛰기",
        "광고 건너뛰기",
        "광고 건너뛰기 버튼",
        "광고 모두 건너뛰기",
        "모두 건너뛰기",
        "Skip",
        "Skip Ad",
        "Skip Ads",
        "Skip ad",
        "Skip ads"
    )

    private val countdownKeywords = listOf(
        "초 후",
        "초 뒤",
        "초후",
        "초뒤",
        "남음",
        "원본 영상",
        "영상이 곧",
        "재생됩니다",
        "seconds",
        "sec",
        " in "
    )

    private val knownSkipViewIds = listOf(
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

        val pkgName = event.packageName?.toString() ?: ""
        val rootNode = rootInActiveWindow
        val activePkg = rootNode?.packageName?.toString() ?: ""

        val isBlacklisted = pkgName == "com.example.tvadskipper" ||
                            activePkg == "com.example.tvadskipper" ||
                            pkgName.contains("launcher", ignoreCase = true) ||
                            pkgName.contains("settings", ignoreCase = true) ||
                            pkgName.contains("setupwraith", ignoreCase = true) ||
                            activePkg.contains("launcher", ignoreCase = true) ||
                            activePkg.contains("settings", ignoreCase = true)

        if (isBlacklisted) {
            return
        }

        val isYouTubeContext = activePkg == "com.google.android.youtube.tv" ||
                               activePkg == "com.google.android.youtube" ||
                               pkgName == "com.google.android.youtube.tv" ||
                               pkgName == "com.google.android.youtube"

        if (!isYouTubeContext) {
            return
        }

        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastClickTime < clickCooldownMs) {
            return
        }

        try {
            val shortsAutoDismiss = prefs.getBoolean(KEY_SHORTS_AUTO_DISMISS, true)
            if (shortsAutoDismiss && rootNode != null) {
                if (checkAndDismissShortsDialog(rootNode, currentTime)) {
                    return
                }
            }

            val qrAdAutoDismiss = prefs.getBoolean(KEY_QR_AD_AUTO_DISMISS, true)
            if (qrAdAutoDismiss && rootNode != null) {
                if (checkAndDismissQrAdOverlay(rootNode, currentTime)) {
                    return
                }
            }

            var clicked = false
            if (rootNode != null) {
                clicked = processNodeTree(rootNode, currentTime)
            }

            if (!clicked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (window in windows) {
                    val wRoot = window.root ?: continue
                    if (processNodeTree(wRoot, currentTime)) {
                        clicked = true
                        break
                    }
                }
            }

            if (clicked) return

            val isAdContext = isCurrentScreenInAdContext(rootNode)
            val isChatVisible = isChatUiVisible(rootNode)
            if (!isChatVisible && (isAdContext || qrAdAutoDismiss)) {
                val pixelSensorEnabled = prefs.getBoolean(KEY_PIXEL_SENSOR_ENABLED, true)
                if (pixelSensorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    analyzeScreenPixelSensor(currentTime, isAdContext)
                } else if (isAdContext) {
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

    private fun isChatUiVisible(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val chatKeywords = listOf("채팅", "실시간 채팅", "Chat", "Live chat")
        for (kw in chatKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(kw)
            if (!nodes.isNullOrEmpty()) return true
        }
        return false
    }

    private fun isCurrentScreenInAdContext(root: AccessibilityNodeInfo?): Boolean {
        try {
            if (LoungeManager.getInstance(this).isAdPlaying) {
                return true
            }
        } catch (_: Exception) {}

        if (root == null) return false

        val adKeywords = listOf("광고", "Ad", "Sponsored", "스폰서", "광고 정보", "초 후", "초 뒤", "건너뛰기", "Skip")
        for (kw in adKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(kw)
            if (!nodes.isNullOrEmpty()) {
                return true
            }
        }

        for (viewId in knownSkipViewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                return true
            }
        }

        return false
    }

    private fun checkAndDismissShortsDialog(root: AccessibilityNodeInfo, currentTime: Long): Boolean {
        // ⚠️ 계정/로그아웃 보호: 사용자가 명시적으로 켰을 때만 작동 (기본값 false)
        val shortsAutoDismiss = prefs.getBoolean(KEY_SHORTS_AUTO_DISMISS, false)
        if (!shortsAutoDismiss || isChatUiVisible(root)) return false

        // 계정 설정, 로그인, 프로필 관련 화면에서는 절대 동작 금지!
        val accountKeywords = listOf("계정", "로그인", "로그아웃", "account", "sign in", "sign out", "비밀번호")
        for (ak in accountKeywords) {
            if (!root.findAccessibilityNodeInfosByText(ak).isNullOrEmpty()) {
                return false
            }
        }

        var hasDisconnectPrompt = false
        for (kw in disconnectDialogKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(kw)
            if (!nodes.isNullOrEmpty()) {
                hasDisconnectPrompt = true
                break
            }
        }

        if (!hasDisconnectPrompt) return false

        Log.i(TAG, "⚡ Detected casting disconnect prompt! Searching for dismiss button...")
        val specificDismissButtons = listOf("전송 해제", "연결 해제", "전송 중지", "Stop casting", "Disconnect")
        for (btnKw in specificDismissButtons) {
            val btnNodes = root.findAccessibilityNodeInfosByText(btnKw)
            if (!btnNodes.isNullOrEmpty()) {
                for (btnNode in btnNodes) {
                    if (tryPerformClick(btnNode)) {
                        lastClickTime = currentTime
                        Log.i(TAG, "⚡ Successfully clicked disconnect confirmation button: $btnKw")
                        AppLogger.i(TAG, "⚡ Successfully clicked disconnect confirmation button: $btnKw")
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun checkAndDismissQrAdOverlay(root: AccessibilityNodeInfo, currentTime: Long): Boolean {
        if (currentTime - lastQrDismissTime < qrDismissCooldownMs) {
            return false
        }

        val displayMetrics: DisplayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        var qrAdDetected = false

        for (kw in qrShoppingKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(kw)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.isEmpty || rect.centerX() < screenWidth * 0.6f) {
                        qrAdDetected = true
                        break
                    }
                }
            }
            if (qrAdDetected) break
        }

        if (!qrAdDetected) {
            return false
        }

        Log.i(TAG, "🎯 Detected QR Shopping Ad Overlay! Searching for close button...")
        AppLogger.i(TAG, "🎯 Detected QR Shopping Ad Overlay! Searching for close button...")

        for (closeKw in qrCloseKeywords) {
            val btnNodes = root.findAccessibilityNodeInfosByText(closeKw)
            if (!btnNodes.isNullOrEmpty()) {
                for (btnNode in btnNodes) {
                    val rect = Rect()
                    btnNode.getBoundsInScreen(rect)
                    if (rect.isEmpty || (rect.centerX() < screenWidth * 0.5f && rect.centerY() > screenHeight * 0.5f)) {
                        if (tryPerformClick(btnNode)) {
                            onQrAdDismissed(currentTime, "Close Node ($closeKw)")
                            return true
                        }
                    }
                }
            }
        }

        dismissQrShoppingOverlay(screenWidth, screenHeight, currentTime, null, "Accessibility Keyword Fallback")
        return true
    }

    /**
     * 좌측 하단 QR 쇼핑 오버레이 닫기:
     * ⚠️ 주의: 리모컨 OK(ENTER)를 누르면 화면이 반으로 갈라지며 우측 광고 상세창이 열리므로
     * 절대 ENTER 키는 보내지 않고, 오직 X 버튼 화면 좌표(동적 계산 좌표 및 12.4%, 89.3%)에만 터치 제스처 발사!
     */
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
            clickAtCoordinates(targetX, targetY)
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

    private fun processNodeTree(root: AccessibilityNodeInfo, currentTime: Long): Boolean {
        for (viewId in knownSkipViewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (tryPerformClick(node)) {
                        onAdSkipped(currentTime, "ViewId: $viewId")
                        return true
                    }
                }
            }
        }

        for (keyword in skipKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val text = node.text?.toString() ?: ""
                    val contentDesc = node.contentDescription?.toString() ?: ""

                    if (isCountdownText(text) || isCountdownText(contentDesc) ||
                        isInvalidSkipText(text) || isInvalidSkipText(contentDesc)) {
                        continue
                    }

                    if (tryPerformClick(node)) {
                        onAdSkipped(currentTime, "Keyword: $keyword")
                        return true
                    }
                }
            }
        }

        val recursiveMatch = findSkipNodeRecursive(root, 0)
        if (recursiveMatch != null) {
            if (tryPerformClick(recursiveMatch)) {
                onAdSkipped(currentTime, "Recursive Node Match")
                return true
            }
        }

        return false
    }

    private fun isInvalidSkipText(text: String): Boolean {
        if (text.isEmpty()) return false
        // 스킵 버튼은 항상 짧음 (25자 초과 시 영상 제목/설명이므로 무시)
        if (text.length > 25) return true
        // 우리 앱 자신이 띄운 토스트 문구 배제
        if (text.contains("자동 실행") || text.contains("회)") || text.contains("완료") || text.contains("설정")) {
            return true
        }
        return false
    }

    private fun findSkipNodeRecursive(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 25) return null

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        if (isInvalidSkipText(text) || isInvalidSkipText(desc)) {
            return null
        }

        if (viewId.contains("skip_ad", ignoreCase = true) || viewId.contains("ad_skip", ignoreCase = true)) {
            if (!isCountdownText(text) && !isCountdownText(desc)) {
                return node
            }
        }

        val matchesSkipText = skipKeywords.any { kw ->
            text.contains(kw, ignoreCase = true) || desc.contains(kw, ignoreCase = true)
        }
        if (matchesSkipText && !isCountdownText(text) && !isCountdownText(desc)) {
            return node
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSkipNodeRecursive(child, depth + 1)
            if (found != null) return found
        }

        return null
    }

    private fun isCountdownText(text: String): Boolean {
        if (text.isEmpty()) return false
        return countdownKeywords.any { cd -> text.contains(cd, ignoreCase = true) } && text.any { it.isDigit() }
    }

    private fun tryPerformClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        var executed = false

        // ⚠️ Google TV 리모컨 포커스 보호: ACTION_FOCUS, ACTION_SELECT 전면 제거!
        // 포커스를 억지로 뺏으면 계정/프로필 메뉴로 리모컨이 튀거나 로그아웃 위험 발생.
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

        // Lounge의 순수 skipAd 전용 명령 동시 발송
        try {
            LoungeManager.getInstance(this).sendSkipAdCommand()
        } catch (_: Exception) {}

        // ⚠️ 노드 클릭이 이미 성공(executed == true)했다면 다른 화면 요소를 건드릴 수 있는 가상 터치는 생략!
        if (!executed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val displayMetrics: DisplayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val rect = Rect()
            node.getBoundsInScreen(rect)

            val (targetX, targetY) = if (rect.width() > 10 && rect.height() > 10 &&
                rect.centerX() > screenWidth * 0.5f && rect.centerY() > screenHeight * 0.5f) {
                Pair(rect.centerX().toFloat(), rect.centerY().toFloat())
            } else {
                Pair(
                    screenWidth * prefs.getFloat(KEY_CUSTOM_X_PERCENT, 0.895f),
                    screenHeight * prefs.getFloat(KEY_CUSTOM_Y_PERCENT, 0.895f)
                )
            }

            Log.d(TAG, "Fallback 2-step skip click gesture at ($targetX, $targetY)")
            performDoubleTapSkipGesture(targetX, targetY)
            executed = true
        }

        return executed
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.N)
    private fun performDoubleTapSkipGesture(targetX: Float, targetY: Float) {
        clickAtCoordinates(targetX, targetY)
        mainHandler.postDelayed({
            if (isServiceRunning) {
                Log.d(TAG, "⚡ [Skip 2nd Tap] Confirming skip click at ($targetX, $targetY)")
                clickAtCoordinates(targetX, targetY)
            }
        }, 130L)
    }

    /**
     * [컴퓨터 비전 픽셀 센서]:
     * ⚠️ accessibility_service_config.xml에 android:canTakeScreenshot="true" 필수!
     * 또한 안드로이드 11/12의 캡처 최소 주기 제한(ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT = 3)을 준수하기 위해 1.15초 쿨다운 적용
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun analyzeScreenPixelSensor(currentTime: Long, isAdContext: Boolean) {
        if (isAnalyzingScreenshot) return
        if (currentTime - lastScreenshotTime < minScreenshotIntervalMs) return

        isAnalyzingScreenshot = true
        lastScreenshotTime = currentTime

        takeScreenshot(Display.DEFAULT_DISPLAY, applicationContext.mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshotResult: ScreenshotResult) {
                try {
                    val bitmap = Bitmap.wrapHardwareBuffer(
                        screenshotResult.hardwareBuffer,
                        screenshotResult.colorSpace
                    )?.copy(Bitmap.Config.ARGB_8888, false)

                    screenshotResult.hardwareBuffer.close()

                    if (bitmap == null) {
                        AppLogger.w(TAG, "takeScreenshot returned null bitmap")
                        isAnalyzingScreenshot = false
                        return
                    }

                    inspectBitmapAndTrigger(bitmap, currentTime, isAdContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Screenshot inspection error", e)
                    AppLogger.e(TAG, "Screenshot inspection error", e)
                } finally {
                    isAnalyzingScreenshot = false
                }
            }

            override fun onFailure(errorCode: Int) {
                Log.w(TAG, "takeScreenshot failed: errorCode=$errorCode")
                // errorCode 2 = ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS (canTakeScreenshot 누락 시 발생)
                // errorCode 3 = ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
                if (errorCode != 3) {
                    AppLogger.w(TAG, "⚠️ takeScreenshot failed: errorCode=$errorCode")
                }
                isAnalyzingScreenshot = false
            }
        })
    }

    private fun inspectBitmapAndTrigger(bitmap: Bitmap, currentTime: Long, isAdContext: Boolean) {
        val width = bitmap.width
        val height = bitmap.height

        // 해상도(720p / 1080p / 4K)에 상관없이 항상 480x270 격자 기준으로 정규화된 샘플링 스텝 사용
        val stepX = max(1, width / 480)
        val stepY = max(1, height / 270)

        // 우하단 스킵 버튼 탐색 ROI (가로 78~96%, 세로 78~94%)
        val startX = (width * 0.78f).toInt()
        val endX = (width * 0.96f).toInt()
        val startY = (height * 0.78f).toInt()
        val endY = (height * 0.94f).toInt()

        var whitePixels = 0
        var yellowPixels = 0
        var blackPixels = 0
        var sumWhiteX = 0L
        var sumWhiteY = 0L

        var x = startX
        while (x < endX) {
            var y = startY
            while (y < endY) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val maxCh = max(r, max(g, b))
                val minCh = min(r, min(g, b))

                if (r > 190 && g > 150 && b < 80) {
                    yellowPixels++
                } else if (r > 195 && g > 195 && b > 195 && (maxCh - minCh) <= 25) {
                    whitePixels++
                    sumWhiteX += x
                    sumWhiteY += y
                } else if (r < 75 && g < 75 && b < 75) {
                    blackPixels++
                }
                y += stepY
            }
            x += stepX
        }

        // 좌측 하단 QR 제휴 쇼핑 카드 정밀 ROI 샘플링 (X: 3~35%, Y: 42~85%)
        val qrAutoDismiss = prefs.getBoolean(KEY_QR_AD_AUTO_DISMISS, true)
        var qrWhitePixels = 0
        var qrDarkPixels = 0
        var sumQrWhiteX = 0L
        var qrTransitions = 0

        if (qrAutoDismiss && currentTime - lastQrDismissTime >= qrDismissCooldownMs) {
            val qrStartX = (width * 0.03f).toInt()
            val qrEndX = (width * 0.35f).toInt()
            val qrStartY = (height * 0.42f).toInt()
            val qrEndY = (height * 0.85f).toInt()

            var qy = qrStartY
            while (qy < qrEndY) {
                var lastColorState = -1 // -1: none, 0: dark, 1: white
                var qx = qrStartX
                while (qx < qrEndX) {
                    val pixel = bitmap.getPixel(qx, qy)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val maxCh = max(r, max(g, b))
                    val minCh = min(r, min(g, b))

                    if (r > 200 && g > 200 && b > 200 && (maxCh - minCh) <= 20) {
                        qrWhitePixels++
                        sumQrWhiteX += qx
                        if (lastColorState == 0) {
                            qrTransitions++
                        }
                        lastColorState = 1
                    } else if (r < 65 && g < 65 && b < 65) {
                        qrDarkPixels++
                        if (lastColorState == 1) {
                            qrTransitions++
                        }
                        lastColorState = 0
                    }
                    qx += stepX
                }
                qy += stepY
            }
        }

        bitmap.recycle()

        Log.d(TAG, "Pixel Sensor ROI (${width}x${height}): White=$whitePixels, Yellow=$yellowPixels, Black=$blackPixels (isAd=$isAdContext), QR_White=$qrWhitePixels, QR_Dark=$qrDarkPixels, Trans=$qrTransitions")

        // 0. 좌측 하단 QR 제휴 쇼핑 카드 검출 시 하단 X 버튼 정밀 타격!
        // QR 격자 교차 패턴(qrTransitions >= 20) 및 흑백 픽셀 수 동시 충족 시에만 확정
        if (qrAutoDismiss && qrWhitePixels in 70..1800 && qrDarkPixels >= 15 && qrTransitions >= 20 && currentTime - lastQrDismissTime >= qrDismissCooldownMs) {
            val qrCenterX = (sumQrWhiteX.toFloat() / qrWhitePixels)
            val dynamicX = if (qrCenterX in (width * 0.08f)..(width * 0.28f)) qrCenterX else (width * 0.124f)
            dismissQrShoppingOverlay(
                width,
                height,
                currentTime,
                dynamicX,
                "Pixel Sensor (QR_W: $qrWhitePixels, QR_D: $qrDarkPixels, Trans: $qrTransitions)"
            )
            return
        }

        if (currentTime - lastClickTime < clickCooldownMs) return

        // 1. 우하단 스킵 버튼 검출: ⚠️ 일반 영상 시청 중 오감지 방지를 위해 isAdContext == true 필수!
        if (!isAdContext) {
            return
        }

        val isSkipButtonDetected = whitePixels >= 45 && blackPixels >= 5

        if (isSkipButtonDetected) {
            val centroidX = (sumWhiteX.toFloat() / whitePixels)
            val centroidY = (sumWhiteY.toFloat() / whitePixels)

            val targetX = if (centroidX in (width * 0.78f)..(width * 0.96f)) {
                centroidX
            } else {
                width * prefs.getFloat(KEY_CUSTOM_X_PERCENT, 0.895f)
            }
            val targetY = if (centroidY in (height * 0.78f)..(height * 0.94f)) {
                centroidY
            } else {
                height * prefs.getFloat(KEY_CUSTOM_Y_PERCENT, 0.895f)
            }

            Log.i(TAG, "👁️ [Pixel Sensor] Skip Button confirmed (W:$whitePixels, B:$blackPixels)! Clicking ($targetX, $targetY)")
            AppLogger.i(TAG, "👁️ [Pixel Sensor] Skip Button confirmed (W:$whitePixels, B:$blackPixels)! Clicking ($targetX, $targetY)")

            try {
                LoungeManager.getInstance(this).sendSkipAdCommand()
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                performDoubleTapSkipGesture(targetX, targetY)
                onAdSkipped(currentTime, "Pixel Sensor (${targetX.toInt()}, ${targetY.toInt()})")
            }
            return
        }

        if (yellowPixels > 25) {
            Log.d(TAG, "Yellow countdown timer active ($yellowPixels px), waiting...")
            return
        }
    }

    private fun performCobaltFallbackClick(currentTime: Long) {
        val displayMetrics: DisplayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val xPercent = prefs.getFloat(KEY_CUSTOM_X_PERCENT, 0.895f)
        val yPercent = prefs.getFloat(KEY_CUSTOM_Y_PERCENT, 0.895f)

        val targetX = width * xPercent
        val targetY = height * yPercent

        Log.d(TAG, "Cobalt fallback double-tap click at ($targetX, $targetY)")
        try {
            LoungeManager.getInstance(this).sendSkipAdCommand()
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            performDoubleTapSkipGesture(targetX, targetY)
            onAdSkipped(currentTime, "Cobalt Fallback")
        }
    }

    fun triggerDirectScreenSkip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastClickTime < clickCooldownMs) return

        val rootNode = rootInActiveWindow
        if (rootNode != null && processNodeTree(rootNode, currentTime)) {
            return
        }

        val displayMetrics: DisplayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val xPercent = prefs.getFloat(KEY_CUSTOM_X_PERCENT, 0.895f)
        val yPercent = prefs.getFloat(KEY_CUSTOM_Y_PERCENT, 0.895f)
        val targetX = width * xPercent
        val targetY = height * yPercent

        AppLogger.i(TAG, "⚡ [Hybrid-Skip] Triggering 2-step direct screen click from external signal at ($targetX, $targetY)")
        performDoubleTapSkipGesture(targetX, targetY)
        onAdSkipped(currentTime, "Hybrid External Signal")
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
        Log.i(TAG, "Successfully skipped YouTube Ad! ($triggerInfo)")
        AppLogger.i(TAG, "🎯 [Accessibility] Successfully skipped YouTube Ad! ($triggerInfo)")

        try {
            LoungeManager.getInstance(this).forceRestoreAudio()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to restore audio from onAdSkipped", e)
        }

        if (::prefs.isInitialized) {
            bootSkipCount++
            prefs.edit()
                .putInt(KEY_SKIP_COUNT, bootSkipCount)
                .putLong(KEY_LAST_SKIP_TIME, System.currentTimeMillis())
                .apply()

            Log.i(TAG, "⚡ Ad skipped successfully (boot count: $bootSkipCount)")
            AppLogger.i(TAG, "⚡ Ad skipped successfully (boot count: $bootSkipCount)")
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
