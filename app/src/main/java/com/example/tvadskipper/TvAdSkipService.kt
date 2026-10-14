package com.example.tvadskipper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
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
    private val qrDismissCooldownMs = 2000L
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
        mainHandler.postDelayed(visualSentinelRunnable, 2000L)
        Log.i(TAG, "👁️ Visual Sentinel loop started (interval: ${qrSentinelIntervalMs}ms)")
        AppLogger.i(TAG, "👁️ Visual Sentinel loop started (interval: ${qrSentinelIntervalMs}ms)")
    }

    private fun stopVisualSentinelLoop() {
        mainHandler.removeCallbacks(visualSentinelRunnable)
        Log.i(TAG, "Visual Sentinel loop stopped.")
    }

    private fun triggerPeriodicVisualCheck() {
        val qrEnabled = prefs.getBoolean(KEY_QR_AD_AUTO_DISMISS, true)
        val pixelEnabled = prefs.getBoolean(KEY_PIXEL_SENSOR_ENABLED, true)
        if (!qrEnabled && !pixelEnabled) return

        val rootNode = rootInActiveWindow
        val activePkg = rootNode?.packageName?.toString() ?: ""
        val isBlacklisted = activePkg.contains("launcher", ignoreCase = true) ||
                            activePkg.contains("settings", ignoreCase = true) ||
                            activePkg.contains("setupwraith", ignoreCase = true)
        if (isBlacklisted) return

        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastQrDismissTime < qrDismissCooldownMs &&
            currentTime - lastClickTime < clickCooldownMs) {
            return
        }

        // 1. 영상 재생 중 접근성 이벤트가 없더라도 센티넬 루프에서 노드 트리 1차 즉시 검사!
        if (qrEnabled && rootNode != null) {
            if (checkAndDismissQrAdOverlay(rootNode, currentTime)) {
                return
            }
        }

        // 2. Cobalt 웹뷰 특성상 노드가 숨겨져 있더라도 픽셀 센서로 2차 화면 검사!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            analyzeScreenPixelSensor(currentTime)
        }
    }

    // 좌측 하단 제휴 쇼핑/QR 광고 오버레이 식별 키워드
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

    // QR 광고 닫기 버튼 키워드
    private val qrCloseKeywords = listOf(
        "닫기",
        "광고 닫기",
        "Close",
        "Dismiss",
        "X",
        "x"
    )

    // 쇼츠 진입 시 캐스팅 전송 해제 팝업 자동 승인 키워드
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

    // 광고 건너뛰기 감지 키워드 목록
    private val skipKeywords = listOf(
        "건너뛰기",
        "Skip",
        "Skip Ad",
        "Skip Ads",
        "광고 건너뛰기",
        "광고 건너뛰기 버튼"
    )

    private val countdownKeywords = listOf(
        "초 후",
        "초 뒤",
        "초후",
        "초뒤",
        "남음",
        "seconds",
        "sec"
    )

    private val knownSkipViewIds = listOf(
        "com.google.android.youtube.tv:id/skip_ad_button",
        "com.google.android.youtube:id/skip_ad_button",
        "com.google.android.youtube.tv:id/ad_skip_button",
        "com.google.android.youtube:id/ad_skip_button"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppLogger.init(applicationContext)
        isServiceRunning = true
        instance = this
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.i(TAG, "TvAdSkipService connected and listening.")
        AppLogger.i(TAG, "TvAdSkipService connected and listening.")
        startVisualSentinelLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: ""
        val rootNode = rootInActiveWindow
        val activePkg = rootNode?.packageName?.toString() ?: ""

        // 1. 홈 화면(launcher), 설정(settings) 등 시스템 메뉴 블랙리스트 원천 차단 (홈화면 날뜀 100% 방지)
        val isBlacklisted = pkgName.contains("launcher", ignoreCase = true) ||
                            pkgName.contains("settings", ignoreCase = true) ||
                            pkgName.contains("setupwraith", ignoreCase = true) ||
                            activePkg.contains("launcher", ignoreCase = true) ||
                            activePkg.contains("settings", ignoreCase = true)

        if (isBlacklisted) {
            return
        }

        // 2. 유튜브 관련 패키지이거나 전체화면 오버레이(android) 컨텍스트일 때 통과
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
            // 0. 쇼츠 진입 시 나타나는 캐스팅 전송 해제 팝업 자동 승인
            val shortsAutoDismiss = prefs.getBoolean(KEY_SHORTS_AUTO_DISMISS, true)
            if (shortsAutoDismiss && rootNode != null) {
                if (checkAndDismissShortsDialog(rootNode, currentTime)) {
                    return
                }
            }

            // 0-1. 본 영상 시청 중 좌측 하단 QR 제휴 쇼핑 광고 오버레이 자동 닫기
            val qrAdAutoDismiss = prefs.getBoolean(KEY_QR_AD_AUTO_DISMISS, true)
            if (qrAdAutoDismiss && rootNode != null) {
                if (checkAndDismissQrAdOverlay(rootNode, currentTime)) {
                    return
                }
            }

            // 1. 활성 창 및 전체 창에서 표준 접근성 노드 탐색
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

            // 2. 컴퓨터 비전 픽셀 센서 및 좌표 클릭은 오직 "실제 광고 상황" 또는 "QR 쇼핑 광고 활성화 모드"일 때 허용!
            val isAdContext = isCurrentScreenInAdContext(rootNode)
            if (isAdContext || qrAdAutoDismiss) {
                val pixelSensorEnabled = prefs.getBoolean(KEY_PIXEL_SENSOR_ENABLED, true)
                if (pixelSensorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    analyzeScreenPixelSensor(currentTime)
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

    private fun isCurrentScreenInAdContext(root: AccessibilityNodeInfo?): Boolean {
        // 1. LoungeManager에서 광고 재생 중임을 확인한 경우
        try {
            if (LoungeManager.getInstance(this).isAdPlaying) {
                return true
            }
        } catch (_: Exception) {}

        if (root == null) return false

        // 2. 화면 노드 트리에 광고 관련 키워드가 실제로 존재하는지 검사
        val adKeywords = listOf("광고", "Ad", "Sponsored", "스폰서", "광고 정보", "초 후", "초 뒤", "건너뛰기")
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

    private fun isCurrentScreenInQrAdContext(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        for (kw in qrShoppingKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(kw)
            if (!nodes.isNullOrEmpty()) {
                return true
            }
        }
        return false
    }

    private fun checkAndDismissShortsDialog(root: AccessibilityNodeInfo, currentTime: Long): Boolean {
        // 채팅 관련 화면이나 다이얼로그인 경우 절대 자동 승인하지 않음
        val chatNodes = root.findAccessibilityNodeInfosByText("채팅")
        val chatEnNodes = root.findAccessibilityNodeInfosByText("Chat")
        if (!chatNodes.isNullOrEmpty() || !chatEnNodes.isNullOrEmpty()) {
            return false
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

        // 1. 화면에 QR 쇼핑 광고 고유 텍스트가 존재하는지 탐색
        var qrAdDetected = false

        for (kw in qrShoppingKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(kw)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    // 화면 좌측 영역(가로 60% 이내)에 위치하는지 검증
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

        // 2. 명시적인 '닫기' / 'X' 버튼 노드가 접근성 트리에 존재하는지 확인
        for (closeKw in qrCloseKeywords) {
            val btnNodes = root.findAccessibilityNodeInfosByText(closeKw)
            if (!btnNodes.isNullOrEmpty()) {
                for (btnNode in btnNodes) {
                    val rect = Rect()
                    btnNode.getBoundsInScreen(rect)
                    // 화면 좌측 하단(가로 50% 이내, 세로 50% 이상)에 위치하는지 확인
                    if (rect.isEmpty || (rect.centerX() < screenWidth * 0.5f && rect.centerY() > screenHeight * 0.5f)) {
                        if (tryPerformClick(btnNode)) {
                            onQrAdDismissed(currentTime, "Close Node ($closeKw)")
                            return true
                        }
                    }
                }
            }
        }

        // 3. Cobalt 웹뷰 특성상 'X' 버튼이 접근성 노드로 잡히지 않는 경우:
        // 정밀 실측 좌표(X: 12.4%, Y: 89.3%) 2단계 연속 터치 제스처 발사!
        dismissQrShoppingOverlay(screenWidth, screenHeight, currentTime, "Accessibility Keyword Fallback")
        return true
    }

    private fun dismissQrShoppingOverlay(width: Int, height: Int, currentTime: Long, triggerInfo: String) {
        lastQrDismissTime = currentTime
        lastClickTime = currentTime

        // 16:9 TV 화면 정밀 실측 타깃 좌표: X = 12.4% (0.124f), Y = 89.3% (0.893f)
        val targetX = width * 0.124f
        val targetY = height * 0.893f

        Log.i(TAG, "🎯 [QR Shopping Ad] Dismissing overlay! ($triggerInfo) Target: ($targetX, $targetY)")
        AppLogger.i(TAG, "🎯 [QR Shopping Ad] Dismissing overlay! ($triggerInfo) Target: ($targetX, $targetY)")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 1차 탭: X 버튼 중심점 타격
            clickAtCoordinates(targetX, targetY)

            // 2차 탭: 120ms 후 중심점 재타격 (Cobalt 웹뷰 터치 씹힘 원천 방지)
            mainHandler.postDelayed({
                if (isServiceRunning) {
                    Log.d(TAG, "🎯 [QR Shopping Ad] 2nd confirm tap at ($targetX, $targetY)")
                    clickAtCoordinates(targetX, targetY)
                }
            }, 120L)
        }

        val showToast = prefs.getBoolean(KEY_SHOW_TOAST, true)
        if (showToast) {
            mainHandler.post {
                Toast.makeText(this, "🎯 [쇼핑 광고] QR 코드 광고 닫기 완료", Toast.LENGTH_SHORT).show()
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

        return false
    }

    private fun isCountdownText(text: String): Boolean {
        if (text.isEmpty()) return false
        return countdownKeywords.any { cd -> text.contains(cd, ignoreCase = true) } && text.any { it.isDigit() }
    }

    private fun tryPerformClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        var executed = false

        // 1. D-PAD 접근성 포커스 사전 부여 시도
        try {
            node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        } catch (_: Exception) {}

        // 2. 표준 접근성 ACTION_CLICK 시도
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) {
                val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    Log.d(TAG, "Clicked node via ACTION_CLICK at depth $depth")
                    executed = true
                    break
                }
            }
            current = current.parent
            depth++
        }

        // 3. Cobalt 웹뷰 필수 연동: 화면 터치 제스처 병행 실행 (Cobalt의 ACTION_CLICK 무시 원천 차단)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                val centerX = rect.centerX().toFloat()
                val centerY = rect.centerY().toFloat()
                Log.d(TAG, "Injecting click gesture at ($centerX, $centerY)")
                clickAtCoordinates(centerX, centerY)
                executed = true
            } else {
                // 웹뷰 가상 노드로 rect가 비어있는 경우 우하단 고정 스킵 좌표로 터치 제스처 발사
                val displayMetrics: DisplayMetrics = resources.displayMetrics
                val targetX = displayMetrics.widthPixels * prefs.getFloat(KEY_CUSTOM_X_PERCENT, 0.895f)
                val targetY = displayMetrics.heightPixels * prefs.getFloat(KEY_CUSTOM_Y_PERCENT, 0.895f)
                Log.d(TAG, "Virtual node fallback click at ($targetX, $targetY)")
                clickAtCoordinates(targetX, targetY)
                executed = true
            }
        }

        return executed
    }

    /**
     * [컴퓨터 비전 픽셀 센서]: 우하단 영역의 실제 색상을 검사하여
     * "흰색 바탕 검은 글씨" 건너뛰기 버튼이 눈에 보일 때만 핀포인트 클릭 실행
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun analyzeScreenPixelSensor(currentTime: Long) {
        if (isAnalyzingScreenshot) return
        isAnalyzingScreenshot = true

        takeScreenshot(Display.DEFAULT_DISPLAY, applicationContext.mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshotResult: ScreenshotResult) {
                try {
                    val bitmap = Bitmap.wrapHardwareBuffer(
                        screenshotResult.hardwareBuffer,
                        screenshotResult.colorSpace
                    )?.copy(Bitmap.Config.ARGB_8888, false)

                    screenshotResult.hardwareBuffer.close()

                    if (bitmap == null) {
                        isAnalyzingScreenshot = false
                        return
                    }

                    inspectBitmapAndTrigger(bitmap, currentTime)
                } catch (e: Exception) {
                    Log.e(TAG, "Screenshot inspection error", e)
                } finally {
                    isAnalyzingScreenshot = false
                }
            }

            override fun onFailure(errorCode: Int) {
                Log.w(TAG, "takeScreenshot failed: errorCode=$errorCode")
                isAnalyzingScreenshot = false
            }
        })
    }

    private fun inspectBitmapAndTrigger(bitmap: Bitmap, currentTime: Long) {
        val width = bitmap.width
        val height = bitmap.height

        // 우하단 타깃 영역 ROI (가로 85~96%, 세로 85~95%)
        val startX = (width * 0.85f).toInt()
        val endX = (width * 0.96f).toInt()
        val startY = (height * 0.85f).toInt()
        val endY = (height * 0.95f).toInt()

        var whitePixels = 0
        var yellowPixels = 0
        var blackPixels = 0
        val step = 4 // 4픽셀 간격 초고속 샘플링

        for (x in startX until endX step step) {
            for (y in startY until endY step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // 1. 노란색 카운터 검출 (#FFD600 계열)
                if (r > 190 && g > 150 && b < 80) {
                    yellowPixels++
                }
                // 2. 흰색 버튼 배경 검출 (#FFFFFF 계열)
                else if (r > 225 && g > 225 && b > 225) {
                    whitePixels++
                }
                // 3. 검은색 텍스트 검출 (#000000 계열)
                else if (r < 60 && g < 60 && b < 60) {
                    blackPixels++
                }
            }
        }

        // 좌측 하단 QR 제휴 쇼핑 카드 정밀 ROI 샘플링 (X: 3~22%, Y: 45~85%)
        val qrAutoDismiss = prefs.getBoolean(KEY_QR_AD_AUTO_DISMISS, true)
        var qrWhitePixels = 0
        var qrDarkPixels = 0

        if (qrAutoDismiss && currentTime - lastQrDismissTime >= qrDismissCooldownMs) {
            val qrStartX = (width * 0.03f).toInt()
            val qrEndX = (width * 0.22f).toInt()
            val qrStartY = (height * 0.45f).toInt()
            val qrEndY = (height * 0.85f).toInt()

            for (x in qrStartX until qrEndX step step) {
                for (y in qrStartY until qrEndY step step) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    // 순백색 카드 배경 (#FFFFFF 계열)
                    if (r > 205 && g > 205 && b > 205) {
                        qrWhitePixels++
                    }
                    // 검은색 QR 도트 패턴 및 글자 (#000000 계열)
                    else if (r < 65 && g < 65 && b < 65) {
                        qrDarkPixels++
                    }
                }
            }
        }

        bitmap.recycle()

        Log.d(TAG, "Pixel Sensor ROI: White=$whitePixels, Yellow=$yellowPixels, Black=$blackPixels, QR_White=$qrWhitePixels, QR_Dark=$qrDarkPixels")

        // 0. 좌측 하단 QR 제휴 쇼핑 카드 검출 시 하단 X 버튼 2단계 정밀 타격!
        if (qrAutoDismiss && qrWhitePixels > 80 && qrDarkPixels > 12 && currentTime - lastQrDismissTime >= qrDismissCooldownMs) {
            dismissQrShoppingOverlay(width, height, currentTime, "Pixel Sensor (White: $qrWhitePixels, Dark: $qrDarkPixels)")
            return
        }

        // 1. 최우선 감지: 흰색 버튼 픽셀 덩어리가 출현하고 검은색 텍스트가 함께 감지된 경우!
        // 영상 배경의 노란색 픽셀 유무와 무관하게 흰색 버튼이 감지되었으면 즉시 클릭 실행!
        if (whitePixels > 60 && blackPixels > 6) {
            val xPercent = prefs.getFloat(KEY_CUSTOM_X_PERCENT, 0.895f)
            val yPercent = prefs.getFloat(KEY_CUSTOM_Y_PERCENT, 0.895f)
            val targetX = width * xPercent
            val targetY = height * yPercent

            Log.i(TAG, "👁️ [Pixel Sensor] White Skip Button confirmed! Clicking at ($targetX, $targetY)")
            AppLogger.i(TAG, "👁️ [Pixel Sensor] White Skip Button confirmed! Clicking at ($targetX, $targetY)")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                clickAtCoordinates(targetX, targetY)
                onAdSkipped(currentTime, "Pixel Sensor (White Button Confirmed)")
            }
            return
        }

        // 2. 보조 가드: 흰색 버튼이 아직 없고 노란색 카운터만 돌아가고 있으면 대기
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

        Log.d(TAG, "Cobalt fallback click at ($targetX, $targetY)")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            clickAtCoordinates(targetX, targetY)
            onAdSkipped(currentTime, "Cobalt Fallback")
        }
    }

    /**
     * LoungeManager 등 외부 모듈에서 스킵 가능 신호를 감지했을 때
     * 화면 상의 스킵 버튼 위치로 즉시 물리 터치 제스처를 쏴주는 하이브리드 협공 함수
     */
    fun triggerDirectScreenSkip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastClickTime < clickCooldownMs) return

        val displayMetrics: DisplayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val xPercent = prefs.getFloat(KEY_CUSTOM_X_PERCENT, 0.895f)
        val yPercent = prefs.getFloat(KEY_CUSTOM_Y_PERCENT, 0.895f)
        val targetX = width * xPercent
        val targetY = height * yPercent

        AppLogger.i(TAG, "⚡ [Hybrid-Skip] Triggering direct screen click from external signal at ($targetX, $targetY)")
        clickAtCoordinates(targetX, targetY)
        onAdSkipped(currentTime, "Hybrid External Signal")
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.N)
    private fun clickAtCoordinates(x: Float, y: Float) {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(clickPath, 0L, 100L))
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

        // 광고 스킵 즉시 Lounge 오디오 복원 연동!
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
        Log.i(TAG, "TvAdSkipService destroyed.")
    }
}
