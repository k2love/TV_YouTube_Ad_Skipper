package com.example.tvadskipper

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LoungeManager private constructor(context: Context) {

    companion object {
        private const val TAG = "LoungeManager"
        private const val BASE_URL = "https://www.youtube.com/api/lounge"
        private const val INNERTUBE_NEXT_URL = "https://www.youtube.com/youtubei/v1/next?prettyPrint=false"

        const val PREFS_KEY_SCREEN_ID = "lounge_screen_id"
        const val PREFS_KEY_LOUNGE_TOKEN = "lounge_token"
        const val PREFS_KEY_DEVICE_NAME = "lounge_device_name"
        const val PREFS_KEY_LOUNGE_SKIP_COUNT = "lounge_skip_count"
        const val PREFS_KEY_LAST_SKIP_TIME = "lounge_last_skip_time"
        const val PREFS_KEY_LAST_EVENT_DESC = "lounge_last_event_desc"
        const val PREFS_KEY_AUTO_MUTE_ENABLED = "lounge_auto_mute_enabled"
        const val PREFS_KEY_SAVED_VOLUME = "lounge_saved_volume"
        const val PREFS_KEY_SAVED_SYS_VOLUME = "lounge_saved_sys_volume"

        @Volatile
        private var instance: LoungeManager? = null

        fun getInstance(context: Context): LoungeManager {
            return instance ?: synchronized(this) {
                instance ?: LoungeManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContext: Context = context.applicationContext
    private val audioManager: AudioManager? = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val prefs: SharedPreferences = appContext.getSharedPreferences("lounge_prefs", Context.MODE_PRIVATE)
    private val loungeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val metadataClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private var sid: String? = null
    private var gsessionid: String? = null
    private val commandOffset = AtomicInteger(1)
    private var lastAid = 0

    @Volatile
    private var currentVolume = 100
    @Volatile
    private var savedVolumeBeforeMute = 100
    @Volatile
    private var savedSystemVolume = 10
    @Volatile
    var isAutoMuted = false
        private set
    @Volatile
    var isAdPlaying = false
        private set
    @Volatile
    var isCurrentAdSkippable = false
        private set

    @Volatile
    private var adStartTime = 0L
    @Volatile
    private var lastAdSignalTime = 0L
    @Volatile
    private var lastMuteCommandSentTime = 0L
    @Volatile
    private var currentMainVideoId = ""
    @Volatile
    private var lastCheckedShoppingVideoId = ""
    @Volatile
    private var lastEventDescCache = "대기 중"
    @Volatile
    private var lastEventPrefsSaveTime = 0L

    private var muteTimeoutJob: Job? = null
    private var muteConfirmJob: Job? = null
    private var restoreVolumeJob: Job? = null
    private var adPodBridgeJob: Job? = null
    private var adEndVerifyJob: Job? = null
    private var adSkipCountdownJob: Job? = null
    private var shoppingMetadataJob: Job? = null

    @Volatile
    private var bootSkipCount = 0

    init {
        bootSkipCount = 0
        savedVolumeBeforeMute = prefs.getInt(PREFS_KEY_SAVED_VOLUME, 100).coerceIn(10, 100)
        savedSystemVolume = prefs.getInt(PREFS_KEY_SAVED_SYS_VOLUME, 10).coerceAtLeast(1)
        prefs.edit().putInt(PREFS_KEY_LOUNGE_SKIP_COUNT, 0).apply()
    }

    @Volatile
    var isConnected = false
        private set

    var onStatusChanged: ((Boolean, String) -> Unit)? = null
    var onAdSkippedCallback: ((Int) -> Unit)? = null
    var onAutoMuteCallback: ((Boolean, Int) -> Unit)? = null

    fun isPaired(): Boolean {
        return !prefs.getString(PREFS_KEY_SCREEN_ID, null).isNullOrEmpty()
    }

    fun getPairedDeviceName(): String {
        return prefs.getString(PREFS_KEY_DEVICE_NAME, "YouTube on TV") ?: "YouTube on TV"
    }

    fun getSkipCount(): Int {
        return bootSkipCount
    }

    fun resetSkipCount() {
        bootSkipCount = 0
        prefs.edit().putInt(PREFS_KEY_LOUNGE_SKIP_COUNT, 0).apply()
        AppLogger.i(TAG, "🔄 Lounge skip count reset to 0 by user request.")
    }

    fun getLastEventDesc(): String {
        return lastEventDescCache
    }

    suspend fun pairWithCode(pairingCode: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanCode = pairingCode.replace("-", "").replace(" ", "").trim()
        if (cleanCode.length < 9) {
            return@withContext Result.failure(IllegalArgumentException("코드가 너무 짧습니다 (12자리 숫자 입력)"))
        }

        try {
            val formBody = FormBody.Builder()
                .add("pairing_code", cleanCode)
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/pairing/get_screen")
                .post(formBody)
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful || bodyString.isEmpty()) {
                return@withContext Result.failure(Exception("페어링 실패 (HTTP ${response.code}): 코드를 다시 확인해 주세요."))
            }

            val json = JSONObject(bodyString)
            val screen = json.optJSONObject("screen")
                ?: return@withContext Result.failure(Exception("화면 정보 응답 없음: $bodyString"))

            val screenId = screen.getString("screenId")
            val loungeToken = screen.getString("loungeToken")
            val deviceName = screen.optString("name", "YouTube on TV")

            prefs.edit()
                .putString(PREFS_KEY_SCREEN_ID, screenId)
                .putString(PREFS_KEY_LOUNGE_TOKEN, loungeToken)
                .putString(PREFS_KEY_DEVICE_NAME, deviceName)
                .apply()

            Log.i(TAG, "Successfully paired! screenId=$screenId, name=$deviceName")
            AppLogger.i(TAG, "Successfully paired! screenId=$screenId, name=$deviceName")
            Result.success(deviceName)
        } catch (e: Exception) {
            Log.e(TAG, "Pairing exception", e)
            Result.failure(e)
        }
    }

    private fun refreshLoungeToken(): Boolean {
        val screenId = prefs.getString(PREFS_KEY_SCREEN_ID, null) ?: return false
        return try {
            val formBody = FormBody.Builder()
                .add("screen_ids", screenId)
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/pairing/get_lounge_token_batch")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                if (!resp.isSuccessful || bodyStr.isEmpty()) {
                    Log.w(TAG, "refreshLoungeToken failed: HTTP ${resp.code}")
                    AppLogger.w(TAG, "refreshLoungeToken failed: HTTP ${resp.code}")
                    return false
                }
                val json = JSONObject(bodyStr)
                val screens = json.optJSONArray("screens") ?: return false
                if (screens.length() == 0) return false

                val screenObj = screens.getJSONObject(0)
                val newToken = screenObj.optString("loungeToken", "")
                if (newToken.isNotEmpty()) {
                    prefs.edit().putString(PREFS_KEY_LOUNGE_TOKEN, newToken).apply()
                    Log.i(TAG, "🔑 Refreshed loungeToken successfully via get_lounge_token_batch!")
                    AppLogger.i(TAG, "🔑 Refreshed loungeToken successfully via get_lounge_token_batch!")
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshLoungeToken exception", e)
            false
        }
    }

    fun unpair() {
        prefs.edit()
            .remove(PREFS_KEY_SCREEN_ID)
            .remove(PREFS_KEY_LOUNGE_TOKEN)
            .remove(PREFS_KEY_DEVICE_NAME)
            .apply()
        disconnect()
    }

    fun disconnect() {
        isConnected = false
        sid = null
        gsessionid = null
        adSkipCountdownJob?.cancel()
        adPodBridgeJob?.cancel()
        adEndVerifyJob?.cancel()
        muteConfirmJob?.cancel()
        shoppingMetadataJob?.cancel()
        onStatusChanged?.invoke(false, "연결 끊김")
    }

    suspend fun startListeningLoop() = withContext(Dispatchers.IO) {
        while (coroutineContext.isActive) {
            if (!isPaired()) {
                isConnected = false
                onStatusChanged?.invoke(false, "TV 페어링 필요")
                delay(3000)
                continue
            }

            try {
                if (sid == null || gsessionid == null) {
                    onStatusChanged?.invoke(false, "구글 서버 세션 연결 시도 중...")
                    var connected = connectSession()
                    if (!connected) {
                        if (refreshLoungeToken()) {
                            connected = connectSession()
                        }
                    }
                    if (!connected) {
                        Log.w(TAG, "connectSession returned false, retry in 5s")
                        delay(5000)
                        continue
                    }
                }

                onStatusChanged?.invoke(true, "유튜브 TV 실시간 감시 작동 중")
                // 세션 연결 직후 광고가 없으면 접근성 서비스를 즉시 동면 모드로 전환하여 TV 성능 보호
                if (!isAdPlaying) {
                    TvAdSkipService.instance?.onAdEndedSleep()
                }
                subscribeStream()
            } catch (e: Exception) {
                Log.w(TAG, "Lounge loop error, reconnecting in 5s: ${e.message}")
                isConnected = false
                sid = null
                gsessionid = null
                onStatusChanged?.invoke(false, "재연결 대기 중...")
                delay(5000)
            }
        }
    }

    private fun connectSession(): Boolean {
        val screenId = prefs.getString(PREFS_KEY_SCREEN_ID, null) ?: return false
        var loungeToken = prefs.getString(PREFS_KEY_LOUNGE_TOKEN, null)
        if (loungeToken.isNullOrEmpty()) {
            if (!refreshLoungeToken()) return false
            loungeToken = prefs.getString(PREFS_KEY_LOUNGE_TOKEN, null) ?: return false
        }

        val url = "$BASE_URL/bc/bind".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("RID", "1")
            .addQueryParameter("VER", "8")
            .addQueryParameter("CVER", "1")
            .addQueryParameter("auth_failure_option", "send_error")
            .build()

        val formBody = FormBody.Builder()
            .add("app", "web")
            .add("mdx-version", "3")
            .add("name", "TvAdSkipper")
            .add("id", screenId)
            .add("device", "REMOTE_CONTROL")
            .add("capabilities", "dsdtr,atp,vsp")
            .add("magnaKey", "cloudPairedDevice")
            .add("ui", "false")
            .add("deviceContext", "user_agent=dunno&window_width_points=&window_height_points=&os_name=android&ms=")
            .add("theme", "cl")
            .add("loungeIdToken", loungeToken)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        val response = client.newCall(request).execute()
        if (response.code == 401) {
            Log.w(TAG, "Lounge token expired (HTTP 401). Clearing token for refresh.")
            AppLogger.w(TAG, "Lounge token expired (HTTP 401). Clearing token for refresh.")
            response.close()
            prefs.edit().remove(PREFS_KEY_LOUNGE_TOKEN).apply()
            return false
        }
        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to connect session: HTTP ${response.code}")
            response.close()
            return false
        }

        val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
        parseChunkStream(reader)
        response.close()

        isConnected = sid != null && gsessionid != null
        commandOffset.set(1)
        Log.i(TAG, "Handshake completed: isConnected=$isConnected, sid=$sid, gsessionid=$gsessionid")
        AppLogger.i(TAG, "Handshake completed: isConnected=$isConnected, sid=$sid")
        return isConnected
    }

    private fun subscribeStream() {
        val currentSid = sid ?: return
        val currentGsession = gsessionid ?: return
        val loungeToken = prefs.getString(PREFS_KEY_LOUNGE_TOKEN, null) ?: return

        val url = "$BASE_URL/bc/bind".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("name", "TvAdSkipper")
            .addQueryParameter("loungeIdToken", loungeToken)
            .addQueryParameter("SID", currentSid)
            .addQueryParameter("AID", lastAid.toString())
            .addQueryParameter("gsessionid", currentGsession)
            .addQueryParameter("device", "REMOTE_CONTROL")
            .addQueryParameter("app", "youtube-desktop")
            .addQueryParameter("VER", "8")
            .addQueryParameter("v", "2")
            .addQueryParameter("RID", "rpc")
            .addQueryParameter("CI", "0")
            .addQueryParameter("TYPE", "xmlhttp")
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "Subscribe returned HTTP ${response.code}, resetting session")
            if (response.code == 401) {
                prefs.edit().remove(PREFS_KEY_LOUNGE_TOKEN).apply()
            }
            response.close()
            sid = null
            gsessionid = null
            return
        }

        val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
        parseChunkStream(reader)
        response.close()
    }

    private fun parseChunkStream(reader: BufferedReader) {
        var chunkRemaining = 0
        val currentChunk = StringBuilder()
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val l = line ?: continue
            if (chunkRemaining <= 0) {
                val trimmed = l.trim()
                if (trimmed.isNotEmpty() && trimmed.all { it.isDigit() }) {
                    chunkRemaining = trimmed.toInt()
                    currentChunk.setLength(0)
                }
            } else {
                currentChunk.append(l)
                chunkRemaining -= (l.length + 1)

                if (chunkRemaining <= 0) {
                    processEventsJson(currentChunk.toString())
                    currentChunk.setLength(0)
                    chunkRemaining = 0
                }
            }
        }
    }

    private fun processEventsJson(rawJson: String) {
        try {
            val eventsArray = JSONArray(rawJson)
            for (i in 0 until eventsArray.length()) {
                val eventItem = eventsArray.optJSONArray(i) ?: continue
                if (eventItem.length() < 2) continue

                val aid = eventItem.optInt(0, lastAid)
                if (aid > lastAid) {
                    lastAid = aid
                }

                val eventTuple = eventItem.optJSONArray(1) ?: continue
                if (eventTuple.length() < 1) continue

                val eventType = eventTuple.getString(0)

                if (eventType == "c") {
                    sid = eventTuple.optString(1, "")
                    Log.i(TAG, "Captured SID: $sid")
                } else if (eventType == "S") {
                    gsessionid = eventTuple.optString(1, "")
                    Log.i(TAG, "Captured gsessionid: $gsessionid")
                } else {
                    val payload = eventTuple.optJSONObject(1)
                    handleAppEvent(eventType, payload)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "processEventsJson error: $rawJson", e)
        }
    }

    fun isAutoMuteEnabled(): Boolean {
        return prefs.getBoolean(PREFS_KEY_AUTO_MUTE_ENABLED, true)
    }

    fun setAutoMuteEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREFS_KEY_AUTO_MUTE_ENABLED, enabled).apply()
        if (!enabled && isAutoMuted) {
            restoreAudio()
        }
    }

    fun onAdDetectedFromLocalUi(isCountdownActive: Boolean) {
        val now = SystemClock.elapsedRealtime()
        lastAdSignalTime = now
        adPodBridgeJob?.cancel()
        adEndVerifyJob?.cancel()

        if (!isAdPlaying) {
            isAdPlaying = true
            adStartTime = now
            AppLogger.i(TAG, "⚡ [Fast-UI-AdDetect] Ad detected locally on screen (countdown=$isCountdownActive)! Muting in 0ms.")
            TvAdSkipService.instance?.onAdStartedWakeUp()
        }
        if (isCountdownActive) {
            isCurrentAdSkippable = false
        }
        if (isAutoMuteEnabled()) {
            muteAudio()
        }
    }

    fun onAccessibilitySkipClicked() {
        sendSkipAdCommand()
    }

    /**
     * 🔍 [비픽셀 QR 쇼핑 광고 사전 판별 엔진]:
     * 상시 화면 캡처(takeScreenshot)로 TV 성능을 깎아먹는 대신,
     * 새 영상(videoId)이 시작될 때 YouTube Innertube API(/youtubei/v1/next)로 해당 영상에
     * 제휴 쇼핑 상품(shopping/merchandise/featuredProduct) 메타데이터가 존재하는지 단 1회 네트워크 조회!
     * - 쇼핑 태그가 없는 95% 이상의 일반 영상에서는 아무 동작도 하지 않아 4K 60fps 끊김 0%!
     * - 쇼핑 태그가 있는 영상에서만 본 영상 진입 후 +4초, +8초에 핀포인트 닫기를 수행!
     */
    private fun checkVideoShoppingMetadataAsync(videoId: String) {
        if (videoId.isEmpty() || videoId == lastCheckedShoppingVideoId) return
        lastCheckedShoppingVideoId = videoId

        shoppingMetadataJob?.cancel()
        shoppingMetadataJob = loungeScope.launch {
            try {
                val requestJson = """
                    {
                      "context": {
                        "client": {
                          "clientName": "TVHTML5",
                          "clientVersion": "7.20260401.10.00",
                          "hl": "ko",
                          "gl": "KR"
                        }
                      },
                      "videoId": "$videoId"
                    }
                """.trimIndent()

                val body = requestJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url(INNERTUBE_NEXT_URL)
                    .post(body)
                    .build()

                val hasShoppingTag = metadataClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use false
                    val raw = resp.body?.string() ?: return@use false
                    raw.contains("shoppingOverlay", ignoreCase = true) ||
                        raw.contains("featuredProduct", ignoreCase = true) ||
                        raw.contains("merchandiseShelf", ignoreCase = true) ||
                        raw.contains("productListRenderer", ignoreCase = true) ||
                        raw.contains("shoppingDrawer", ignoreCase = true) ||
                        raw.contains("수수료 지급") ||
                        raw.contains("제품 보기")
                }

                if (!hasShoppingTag) {
                    Log.d(TAG, "✅ [Innertube-ShoppingCheck] No shopping tags in video ($videoId). Staying in 0% CPU deep sleep.")
                    return@launch
                }

                AppLogger.i(TAG, "🛒 [Innertube-ShoppingCheck] Shopping product metadata detected in video ($videoId)! Scheduling targeted QR check.")
                // 본 영상 시작 후 QR 카드가 등장하는 +4.0초, +8.0초 시점에만 핀포인트 확인
                delay(4000L)
                if (!isAdPlaying && currentMainVideoId == videoId) {
                    TvAdSkipService.instance?.performTargetedQrDismissFromMetadata(videoId)
                }
                delay(4000L)
                if (!isAdPlaying && currentMainVideoId == videoId) {
                    TvAdSkipService.instance?.performTargetedQrDismissFromMetadata(videoId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Innertube shopping metadata check skipped: ${e.message}")
            }
        }
    }

    private fun handleAppEvent(eventType: String, payload: JSONObject?) {
        val desc = "$eventType: ${payload?.toString() ?: ""}"
        Log.d(TAG, "Lounge Event -> $desc")
        lastEventDescCache = desc

        val now = SystemClock.elapsedRealtime()
        if (now - lastEventPrefsSaveTime >= 2000L) {
            lastEventPrefsSaveTime = now
            prefs.edit().putString(PREFS_KEY_LAST_EVENT_DESC, desc).apply()
        }

        when (eventType) {
            "onVolumeChanged" -> {
                val vol = payload?.optInt("volume", -1) ?: -1
                if (vol in 1..100) {
                    if (!isAutoMuted && !isAdPlaying) {
                        currentVolume = vol
                        savedVolumeBeforeMute = vol
                        prefs.edit().putInt(PREFS_KEY_SAVED_VOLUME, vol).apply()
                    } else if (isAdPlaying && isAutoMuted && isAutoMuteEnabled()) {
                        AppLogger.i(TAG, "🛡️ [Mute-Guard] YouTube raised volume to $vol during active ad! Clamping back to 0 immediately.")
                        applyLocalHardwareMute()
                        loungeScope.launch {
                            sendSetVolumeCommand(0)
                        }
                    }
                }
            }
            "onAdStateChange", "adPlaying" -> {
                val isSkipEnabled = payload?.optString("isSkipEnabled", "false") ?: "false"
                val adState = payload?.optString("adState", "") ?: ""
                val adVideoId = payload?.optString("adVideoId", "") ?: ""
                AppLogger.i(TAG, "📢 Ad Event: adState=$adState, isSkipEnabled=$isSkipEnabled, adVideoId=$adVideoId, isAutoMuted=$isAutoMuted")

                // 1. 실제 광고 슬롯 완전 종료: adState 1082, STOPPED, ENDED
                if (adState == "1082" || adState.equals("STOPPED", ignoreCase = true) || adState.equals("ENDED", ignoreCase = true)) {
                    AppLogger.i(TAG, "📢 Ad slot ended ($adState). Waiting 650ms for potential 2nd ad in pod...")
                    isAdPlaying = false
                    isCurrentAdSkippable = false
                    adSkipCountdownJob?.cancel()
                    adPodBridgeJob?.cancel()
                    adEndVerifyJob?.cancel()
                    adPodBridgeJob = loungeScope.launch {
                        delay(650)
                        val uiStillHasAd = TvAdSkipService.instance?.isAdOrCountdownCurrentlyOnScreen() == true
                        if (!isAdPlaying && !uiStillHasAd) {
                            if (isAutoMuted) {
                                AppLogger.i(TAG, "📢 Ad slot finished confirmed. Restoring audio.")
                                restoreAudio()
                            }
                            // 광고가 끝났으므로 접근성 서비스를 즉시 0% 부하 동면 모드로 전환!
                            TvAdSkipService.instance?.onAdEndedSleep()
                            if (currentMainVideoId.isNotEmpty()) {
                                checkVideoShoppingMetadataAsync(currentMainVideoId)
                            }
                        }
                    }
                    return
                }

                // 2. 광고 진행 상태 설정 및 즉시 음소거 적용 + 접근성 서비스 일시 깨우기(Wake-Up)
                val adIsPlaying = adState == "1" || adState == "0" || adState == "-1" ||
                                  adState.equals("PLAYING", ignoreCase = true) ||
                                  eventType == "adPlaying" || adVideoId.isNotEmpty() ||
                                  isSkipEnabled.equals("true", ignoreCase = true)

                if (adIsPlaying) {
                    adPodBridgeJob?.cancel()
                    adEndVerifyJob?.cancel()
                    if (!this.isAdPlaying) {
                        adStartTime = now
                    }
                    lastAdSignalTime = now
                    this.isAdPlaying = true
                    this.isCurrentAdSkippable = isSkipEnabled.equals("true", ignoreCase = true)

                    // 광고 시간 동안만 접근성 센티넬 깨우기
                    TvAdSkipService.instance?.onAdStartedWakeUp()

                    if (isAutoMuteEnabled()) {
                        muteAudio()
                    }

                    if (this.isCurrentAdSkippable) {
                        AppLogger.i(TAG, "🔥 [Lounge] Ad skip enabled signal! Dispatching skipAd & screen node click!")
                        sendSkipAdCommand()
                        try {
                            TvAdSkipService.instance?.triggerDirectScreenSkip()
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "Failed to trigger direct screen skip", e)
                        }
                    } else {
                        scheduleAdSkipCountdownRetries()
                    }
                }
            }
            "onStateChange" -> {
                val state = payload?.optInt("state", -1) ?: -1
                if (state == 0 && !isAdPlaying && isAutoMuted) {
                    AppLogger.i(TAG, "🎬 [StateChange=0] Video ended with no active ad. Restoring audio.")
                    restoreAudio()
                    TvAdSkipService.instance?.onAdEndedSleep()
                } else if (state == 1 && isAdPlaying) {
                    val elapsedSinceAdStart = now - adStartTime
                    val elapsedSinceLastAdSignal = now - lastAdSignalTime
                    val uiHasAd = TvAdSkipService.instance?.isAdOrCountdownCurrentlyOnScreen() == true
                    if (elapsedSinceAdStart >= 5200L && elapsedSinceLastAdSignal >= 3500L && !uiHasAd) {
                        scheduleMainVideoRestoreVerification("StateChange=1 after ad window")
                    }
                } else if (state == 1 && !isAdPlaying) {
                    if (isAutoMuted) {
                        AppLogger.i(TAG, "🎬 [StateChange=1] Main playback active. Ensuring audio restored.")
                        restoreAudio()
                    }
                    TvAdSkipService.instance?.onAdEndedSleep()
                }
            }
            "nowPlaying" -> {
                val videoId = payload?.optString("videoId", "") ?: ""
                val adState = payload?.optString("adState", "") ?: ""
                val adVideoId = payload?.optString("adVideoId", "") ?: ""
                val isAdEndState = adState == "1082" || adState.equals("STOPPED", ignoreCase = true) || adState.equals("ENDED", ignoreCase = true)
                val hasActiveAdInPayload = (adState.isNotEmpty() && !isAdEndState) || adVideoId.isNotEmpty()

                if (hasActiveAdInPayload) {
                    adPodBridgeJob?.cancel()
                    adEndVerifyJob?.cancel()
                    if (!isAdPlaying) {
                        adStartTime = now
                    }
                    lastAdSignalTime = now
                    isAdPlaying = true
                    if (videoId.isNotEmpty()) {
                        currentMainVideoId = videoId
                    }
                    TvAdSkipService.instance?.onAdStartedWakeUp()
                    if (isAutoMuteEnabled()) {
                        muteAudio()
                    }
                    scheduleAdSkipCountdownRetries()
                } else if (videoId.isEmpty()) {
                    AppLogger.i(TAG, "🎬 [NowPlaying] Empty videoId (Home/Back). Resetting ad state & restoring audio.")
                    isAdPlaying = false
                    isCurrentAdSkippable = false
                    currentMainVideoId = ""
                    adSkipCountdownJob?.cancel()
                    adPodBridgeJob?.cancel()
                    adEndVerifyJob?.cancel()
                    if (isAutoMuted) {
                        restoreAudio()
                    }
                    TvAdSkipService.instance?.onAdEndedSleep()
                } else {
                    val isSameVideo = currentMainVideoId.isEmpty() || currentMainVideoId == videoId
                    currentMainVideoId = videoId
                    val elapsedSinceAdStart = now - adStartTime
                    val elapsedSinceLastAdSignal = now - lastAdSignalTime
                    val uiHasAd = TvAdSkipService.instance?.isAdOrCountdownCurrentlyOnScreen() == true

                    if (isAdPlaying && isSameVideo && (elapsedSinceAdStart < 4800L || elapsedSinceLastAdSignal < 2800L || uiHasAd)) {
                        AppLogger.i(TAG, "🛡️ [Ad-Lock] Ignoring mid-ad nowPlaying packet (elapsed=${elapsedSinceAdStart}ms, uiHasAd=$uiHasAd) to keep forced ad muted!")
                    } else {
                        scheduleMainVideoRestoreVerification("Pure nowPlaying ($videoId)")
                        checkVideoShoppingMetadataAsync(videoId)
                    }
                }
            }
            "loungeScreenDisconnected" -> {
                AppLogger.i(TAG, "Lounge screen disconnected. Clearing session & refreshing token on reconnect.")
                isConnected = false
                sid = null
                gsessionid = null
                isAdPlaying = false
                isCurrentAdSkippable = false
                adSkipCountdownJob?.cancel()
                adPodBridgeJob?.cancel()
                adEndVerifyJob?.cancel()
                restoreAudio()
                onStatusChanged?.invoke(false, "화면 연결 대기 중 (자동 재연결 중)")
            }
        }
    }

    private fun scheduleMainVideoRestoreVerification(reason: String) {
        adEndVerifyJob?.cancel()
        adEndVerifyJob = loungeScope.launch {
            delay(550)
            val now = SystemClock.elapsedRealtime()
            val uiHasAd = TvAdSkipService.instance?.isAdOrCountdownCurrentlyOnScreen() == true
            val recentAdSignal = (now - lastAdSignalTime) < 2200L
            if (!uiHasAd && !recentAdSignal) {
                isAdPlaying = false
                isCurrentAdSkippable = false
                adSkipCountdownJob?.cancel()
                if (isAutoMuted) {
                    AppLogger.i(TAG, "🎬 [Verified-MainVideo] $reason -> Restoring audio now!")
                    restoreAudio()
                }
                // 본 영상 진입 확정 시 접근성 서비스는 즉시 완전 동면(Deep Sleep)으로 전환!
                TvAdSkipService.instance?.onAdEndedSleep()
            } else {
                AppLogger.i(TAG, "🛡️ [Ad-Lock] Blocked premature audio restore ($reason): uiHasAd=$uiHasAd, recentAdSignal=$recentAdSignal")
            }
        }
    }

    private fun scheduleAdSkipCountdownRetries() {
        if (adSkipCountdownJob?.isActive == true) return

        adSkipCountdownJob = loungeScope.launch {
            val checkpointsMs = listOf(5200L, 1300L, 1600L)
            for (waitMs in checkpointsMs) {
                delay(waitMs)
                if (!isAdPlaying || !isConnected) break

                if (TvAdSkipService.instance?.isYellowCountdownActive() == true && !isCurrentAdSkippable) {
                    continue
                }

                AppLogger.i(TAG, "⏱️ [Ad-Countdown-Timer] Probing silent network skipAd")
                sendCommand("skipAd")

                try {
                    TvAdSkipService.instance?.tryClickVisibleSkipNodeOnly()
                } catch (_: Exception) {}
            }
        }
    }

    private fun onSkipCommandSucceeded() {
        bootSkipCount++
        prefs.edit()
            .putInt(PREFS_KEY_LOUNGE_SKIP_COUNT, bootSkipCount)
            .putLong(PREFS_KEY_LAST_SKIP_TIME, System.currentTimeMillis())
            .apply()
        onAdSkippedCallback?.invoke(bootSkipCount)
    }

    private fun applyLocalHardwareMute() {
        try {
            audioManager?.let { am ->
                val curSysVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (curSysVol > 0) {
                    savedSystemVolume = curSysVol
                    prefs.edit().putInt(PREFS_KEY_SAVED_SYS_VOLUME, curSysVol).apply()
                }
                am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "applyLocalHardwareMute error", e)
        }
    }

    private fun restoreLocalHardwareAudio(showOsd: Boolean = false) {
        try {
            audioManager?.let { am ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                }
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val defaultVol = if (maxVol >= 10) 10 else maxVol
                val targetVol = if (savedSystemVolume in 1..maxVol) savedSystemVolume else defaultVol
                val flags = if (showOsd) AudioManager.FLAG_SHOW_UI else 0
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, flags)
            }
        } catch (e: Exception) {
            Log.w(TAG, "restoreLocalHardwareAudio error", e)
        }
    }

    private fun muteAudio() {
        if (!isAutoMuteEnabled()) return

        val now = SystemClock.elapsedRealtime()
        val wasAlreadyMuted = isAutoMuted
        isAutoMuted = true
        if (!wasAlreadyMuted) {
            adStartTime = now
        }

        applyLocalHardwareMute()

        if (!wasAlreadyMuted && currentVolume > 0) {
            savedVolumeBeforeMute = currentVolume
        }

        if (!wasAlreadyMuted || now - lastMuteCommandSentTime >= 450L) {
            lastMuteCommandSentTime = now
            restoreVolumeJob?.cancel()
            muteConfirmJob?.cancel()
            muteConfirmJob = loungeScope.launch {
                val s1 = sendSetVolumeCommand(0)
                AppLogger.i(TAG, "🔇 [Instant-Mute] Volume 0 applied (localHW=0ms, lounge1=$s1, saved=$savedVolumeBeforeMute)")
                if (!wasAlreadyMuted) {
                    delay(280)
                    if (isAutoMuted && isAdPlaying) {
                        applyLocalHardwareMute()
                        sendSetVolumeCommand(0)
                    }
                    delay(550)
                    if (isAutoMuted && isAdPlaying) {
                        applyLocalHardwareMute()
                        sendSetVolumeCommand(0)
                    }
                }
            }
        }

        if (!wasAlreadyMuted) {
            onAutoMuteCallback?.invoke(true, 0)
        }

        muteTimeoutJob?.cancel()
        muteTimeoutJob = loungeScope.launch {
            delay(45000)
            if (isAutoMuted) {
                AppLogger.w(TAG, "⏰ [Safety Timeout] Auto-restoring volume after 45s timeout!")
                isAdPlaying = false
                isCurrentAdSkippable = false
                restoreAudio()
                TvAdSkipService.instance?.onAdEndedSleep()
            }
        }
    }

    private fun restoreAudio() {
        muteTimeoutJob?.cancel()
        muteTimeoutJob = null
        muteConfirmJob?.cancel()
        muteConfirmJob = null
        adPodBridgeJob?.cancel()
        adPodBridgeJob = null
        adEndVerifyJob?.cancel()
        adEndVerifyJob = null

        val sysVolZero = (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 10) == 0
        if (!isAutoMuted && !sysVolZero) return

        isAutoMuted = false
        isAdPlaying = false
        isCurrentAdSkippable = false

        restoreLocalHardwareAudio(showOsd = false)

        val restoreVol = if (savedVolumeBeforeMute in 10..100) savedVolumeBeforeMute else 100
        restoreVolumeJob?.cancel()
        restoreVolumeJob = loungeScope.launch {
            val s1 = sendSetVolumeCommand(restoreVol)
            AppLogger.i(TAG, "🔊 [YouTube-Restore-1] Volume set to $restoreVol% (success=$s1)")
            delay(300)
            if (!isAutoMuted && !isAdPlaying) {
                restoreLocalHardwareAudio(showOsd = false)
                val s2 = sendSetVolumeCommand(restoreVol)
                AppLogger.i(TAG, "🔊 [YouTube-Restore-2] Volume confirmed at $restoreVol% (success=$s2)")
            }
        }

        onAutoMuteCallback?.invoke(false, restoreVol)
    }

    fun forceRestoreAudio() {
        restoreLocalHardwareAudio(showOsd = true)
        savedVolumeBeforeMute = 100
        isAutoMuted = true
        restoreAudio()
    }

    private fun sendCommand(command: String, params: Map<String, String>? = null): Boolean {
        val currentSid = sid ?: return false
        val currentGsession = gsessionid ?: return false
        val loungeToken = prefs.getString(PREFS_KEY_LOUNGE_TOKEN, null) ?: return false

        val offset = commandOffset.getAndIncrement()

        val url = "$BASE_URL/bc/bind".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("name", "TvAdSkipper")
            .addQueryParameter("loungeIdToken", loungeToken)
            .addQueryParameter("SID", currentSid)
            .addQueryParameter("AID", lastAid.toString())
            .addQueryParameter("gsessionid", currentGsession)
            .addQueryParameter("device", "REMOTE_CONTROL")
            .addQueryParameter("app", "youtube-desktop")
            .addQueryParameter("VER", "8")
            .addQueryParameter("v", "2")
            .addQueryParameter("RID", offset.toString())
            .build()

        val formBuilder = FormBody.Builder()
            .add("count", "1")
            .add("ofs", offset.toString())
            .add("req0__sc", command)

        params?.forEach { (k, v) ->
            formBuilder.add("req0_$k", v)
        }

        val request = Request.Builder()
            .url(url)
            .post(formBuilder.build())
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: $command", e)
            false
        }
    }

    fun sendSkipAdCommand() {
        loungeScope.launch {
            val s1 = sendCommand("skipAd")
            AppLogger.i(TAG, "🎯 [Lounge-1] Sent skipAd attempt 1 (success=$s1)")
            delay(350)
            val s2 = sendCommand("skipAd")
            AppLogger.i(TAG, "🎯 [Lounge-2] Sent skipAd attempt 2 (success=$s2)")

            val success = s1 || s2
            if (success) {
                onSkipCommandSucceeded()
                AppLogger.i(TAG, "🎯 [Lounge] Successfully executed double-tap skipAd on TV!")

                delay(900)
                val uiStillHasAd = TvAdSkipService.instance?.isAdOrCountdownCurrentlyOnScreen() == true
                if (!isAdPlaying && !uiStillHasAd) {
                    if (isAutoMuted) {
                        AppLogger.i(TAG, "🎯 [Lounge] Post-skip ad confirmed ended. Restoring audio.")
                        restoreAudio()
                    }
                    TvAdSkipService.instance?.onAdEndedSleep()
                }
            } else {
                AppLogger.w(TAG, "⚠️ [Lounge] skipAd command returned failure or ignored by TV.")
            }
        }
    }

    fun sendSetVolumeCommand(volume: Int): Boolean {
        return sendCommand("setVolume", mapOf("volume" to volume.coerceIn(0, 100).toString()))
    }
}
