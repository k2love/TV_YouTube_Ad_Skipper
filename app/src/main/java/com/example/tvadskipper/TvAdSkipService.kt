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
import android.widget.Toast
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
    private val clickCooldownMs = 800L
    private var lastQrDismissTime = 0L
    private val qrDismissCooldownMs = 2500L
    private var lastScreenshotTime = 0L
    private val minScreenshotIntervalMs = 1150L
    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isAnalyzingScreenshot = false
    private val qrSentinelIntervalMs = 1200L

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
        ensureLoungeServiceAlive()

        val qrEnabled = prefs.getBoolean(KEY_QR_AD_AUTO_DISMISS, true)
        val pixelEnabled = prefs.getBoolean(KEY_PIXEL_SENSOR_ENABLED, true)

        val rootNode = rootInActiveWindow
        val activePkg = rootNode?.packageName?.toString() ?: ""
        val isBlacklisted = activePkg.contains("launcher", ignoreCase = true) ||
                            activePkg.contains("settings", ignoreCase = true) ||
                            activePkg.contains("setupwraith", ignoreCase = true)
        if (isBlacklisted) return

        val isYouTubeOrOverlay = activePkg.isEmpty() ||
                                 activePkg.contains("youtube", ignoreCase = true) ||
                                 activePkg == "android"
        if (!isYouTubeOrOverlay) return

        val currentTime = SystemClock.uptimeMillis()

        // 1. 센티넬 루프에서 우하단 광고 건너뛰기 접근성 노드 탐색
        if (currentTime - lastClickTime >= clickCooldownMs) {
            var skippedByNode = false
            if (rootNode != null) {
                skippedByNode = processNodeTree(rootNode, currentTime)
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
            if (rootNode != null && checkAndDismissQrAdOverlay(rootNode, currentTime)) {
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

        // 3. 픽셀 센서로 우하단 스킵 버튼 & 좌하단 QR 카드 동시 검사 (canTakeScreenshot="true" 필수)
        if (pixelEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (currentTime - lastClickTime >= clickCooldownMs ||
                (qrEnabled && currentTime - lastQrDismissTime >= qrDismissCooldownMs)) {
                val isAdContext = isCurrentScreenInAdContext(rootNode)
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
        Log.i(TAG, "TvAdSkipService connected and listening.")
        AppLogger.i(TAG, "TvAdSkipService connected and listening.")
        ensureLoungeServiceAlive()
        startVisualSentinelLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: ""
        val rootNode = rootInActiveWindow
        val activePkg = rootNode?.packageName?.toString() ?: ""

        val isBlacklisted = pkgName.contains("launcher", ignoreCase = true) ||
                            pkgName.contains("settings", ignoreCase = true) ||
                            pkgName.contains("setupwraith", ignoreCase = true) ||
                            activePkg.contains("launcher", ignoreCase = true) ||
                            activePkg.contains("settings", ignoreCase = true)

        if (isBlacklisted) {
            return
        }

        val isYouTubeContext = pkgName.contains("youtube", ignoreCase = true) ||
                               activePkg.contains("youtube", ignoreCase = true) ||
                               pkgName == "android" || activePkg == "android"

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
        if (isChatUiVisible(root)) return false

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
        for (btnKw in dismissButtonKeywords) {
            val btnNodes = root.findAccessibilityNodeInfosByText(btnKw)
            if (!btnNodes.isNullOrEmpty()) {
                for (btnNode in btnNodes) {
                    if (tryPerformClick(btnNode)) {
                        lastClickTime = currentTime
                        Log.i(TAG, "⚡ Successfully clicked disconnect confirmation button: $btnKw")
                        mainHandler.post {
                            Toast.makeText(this, "⚡ [쇼츠] 캐스팅 전송 해제 팝업 자동 승인 완료", Toast.LENGTH_SHORT).show()
                        }
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

        val showToast = prefs.getBoolean(KEY_SHOW_TOAST, true)
        if (showToast) {
            mainHandler.post {
                Toast.makeText(this, "🎯 [쇼핑 광고] QR 코드 광고 감지 및 닫기 실행", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onQrAdDismissed(currentTime: Long, triggerInfo: String) {
        lastQrDismissTime = currentTime
        lastClickTime = currentTime
        Log.i(TAG, "🎯 Successfully dismissed QR Shopping Ad Overlay! ($triggerInfo)")
        AppLogger.i(TAG, "🎯 Successfully dismissed QR Shopping Ad Overlay! ($triggerInfo)")

        val showToast = prefs.getBoolean(KEY_SHOW_TOAST, true)
        if (showToast) {
            mainHandler.post {
                Toast.makeText(this, "🎯 [쇼핑 광고] QR 코드 광고 자동 닫기 완료", Toast.LENGTH_SHORT).show()
            }
        }
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

                    if (isCountdownText(text) || isCountdownText(contentDesc)) {
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

    private fun findSkipNodeRecursive(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 25) return null

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val viewId = node.viewIdResourceName ?: ""

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

        try {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            node.performAction(AccessibilityNodeInfo.ACTION_SELECT)
        } catch (_: Exception) {}

        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
            try {
                current.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                if (current.isClickable || depth == 0) {
                    val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) {
                        Log.d(TAG, "Clicked node via ACTION_CLICK at depth $depth")
                        executed = true
                        if (current.isClickable) break
                    }
                }
            } catch (_: Exception) {}
            current = current.parent
            depth++
        }

        // Lounge의 순수 skipAd 전용 명령 동시 발송 (우측 상세창을 여는 ENTER는 보내지 않음)
        try {
            LoungeManager.getInstance(this).sendSkipAdCommand()
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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

            Log.d(TAG, "Injecting 2-step skip click gesture at ($targetX, $targetY)")
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

        if (qrAutoDismiss && currentTime - lastQrDismissTime >= qrDismissCooldownMs) {
            val qrStartX = (width * 0.03f).toInt()
            val qrEndX = (width * 0.35f).toInt()
            val qrStartY = (height * 0.42f).toInt()
            val qrEndY = (height * 0.85f).toInt()

            var qx = qrStartX
            while (qx < qrEndX) {
                var qy = qrStartY
                while (qy < qrEndY) {
                    val pixel = bitmap.getPixel(qx, qy)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val maxCh = max(r, max(g, b))
                    val minCh = min(r, min(g, b))

                    if (r > 200 && g > 200 && b > 200 && (maxCh - minCh) <= 20) {
                        qrWhitePixels++
                        sumQrWhiteX += qx
                    } else if (r < 65 && g < 65 && b < 65) {
                        qrDarkPixels++
                    }
                    qy += stepY
                }
                qx += stepX
            }
        }

        bitmap.recycle()

        Log.d(TAG, "Pixel Sensor ROI (${width}x${height}): White=$whitePixels, Yellow=$yellowPixels, Black=$blackPixels (isAd=$isAdContext), QR_White=$qrWhitePixels, QR_Dark=$qrDarkPixels")

        // 0. 좌측 하단 QR 제휴 쇼핑 카드 검출 시 하단 X 버튼 정밀 타격!
        // QR 카드의 가로 중심점(sumQrWhiteX / qrWhitePixels)이 계산되면 그 중심 하단(Y: 89.3%)을 타격하고, 기본값은 12.4% 사용
        if (qrAutoDismiss && qrWhitePixels in 70..1800 && qrDarkPixels >= 12 && currentTime - lastQrDismissTime >= qrDismissCooldownMs) {
            val qrCenterX = (sumQrWhiteX.toFloat() / qrWhitePixels)
            val dynamicX = if (qrCenterX in (width * 0.08f)..(width * 0.28f)) qrCenterX else (width * 0.124f)
            dismissQrShoppingOverlay(
                width,
                height,
                currentTime,
                dynamicX,
                "Pixel Sensor (QR_W: $qrWhitePixels, QR_D: $qrDarkPixels)"
            )
            return
        }

        if (currentTime - lastClickTime < clickCooldownMs) return

        // 1. 우하단 스킵 버튼 검출 시 무게중심 더블탭 + Lounge skipAd 동시 타격
        val isSkipButtonDetected = if (isAdContext) {
            whitePixels >= 40 && blackPixels >= 5
        } else {
            whitePixels in 50..650 && blackPixels >= 6
        }

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
            val count = prefs.getInt(KEY_SKIP_COUNT, 0) + 1
            val showToast = prefs.getBoolean(KEY_SHOW_TOAST, true)
            prefs.edit()
                .putInt(KEY_SKIP_COUNT, count)
                .putLong(KEY_LAST_SKIP_TIME, System.currentTimeMillis())
                .apply()

            if (showToast) {
                mainHandler.post {
                    Toast.makeText(this, "광고 건너뛰기 자동 실행 (${count}회)", Toast.LENGTH_SHORT).show()
                }
            }
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
