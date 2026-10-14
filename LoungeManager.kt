package com.example.tvadskipper

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
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

        const val PREFS_KEY_SCREEN_ID = "lounge_screen_id"
        const val PREFS_KEY_LOUNGE_TOKEN = "lounge_token"
        const val PREFS_KEY_DEVICE_NAME = "lounge_device_name"
        const val PREFS_KEY_LOUNGE_SKIP_COUNT = "lounge_skip_count"
        const val PREFS_KEY_LAST_SKIP_TIME = "lounge_last_skip_time"
        const val PREFS_KEY_LAST_EVENT_DESC = "lounge_last_event_desc"
        const val PREFS_KEY_AUTO_MUTE_ENABLED = "lounge_auto_mute_enabled"
        const val PREFS_KEY_SAVED_VOLUME = "lounge_saved_volume"

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
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
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
    var isAutoMuted = false
        private set
    @Volatile
    var isAdPlaying = false
        private set

    @Volatile
    private var adStartTime = 0L
    @Volatile
    private var lastSkipAdTime = 0L
    private var muteTimeoutJob: Job? = null
    private var adPodBridgeJob: Job? = null
    private var adSkipCountdownJob: Job? = null

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
        return prefs.getInt(PREFS_KEY_LOUNGE_SKIP_COUNT, 0)
    }

    fun getLastEventDesc(): String {
        return prefs.getString(PREFS_KEY_LAST_EVENT_DESC, "대기 중") ?: "대기 중"
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

    /**
     * [핵심 복구] 영구 screenId를 이용해 만료된 loungeToken을 새로 발급받는 함수
     * (pyytlounge의 refresh_auth: /pairing/get_lounge_token_batch 규격과 100% 동일)
     * 이 함수가 없으면 12자리 코드로 한 번 연결된 뒤 몇 시간 후 토큰이 만료(HTTP 401)되었을 때
     * UI에는 '연결됨'으로 표시되지만 실제로는 아무 신호도 못 받는 현상이 발생함!
     */
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
                        // 토큰 만료(401 등) 가능성 -> 즉시 토큰 자동 갱신 후 재시도!
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

    private fun handleAppEvent(eventType: String, payload: JSONObject?) {
        val desc = "$eventType: ${payload?.toString() ?: ""}"
        Log.d(TAG, "Lounge Event -> $desc")
        AppLogger.i("LoungeEvent", desc)
        prefs.edit().putString(PREFS_KEY_LAST_EVENT_DESC, desc).apply()

        when (eventType) {
            "onVolumeChanged" -> {
                val vol = payload?.optInt("volume", -1) ?: -1
                if (vol in 1..100) {
                    if (!isAutoMuted) {
                        currentVolume = vol
                        savedVolumeBeforeMute = vol
                        prefs.edit().putInt(PREFS_KEY_SAVED_VOLUME, vol).apply()
                        AppLogger.d(TAG, "Updated YouTube app volume: $currentVolume")
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
                    AppLogger.i(TAG, "📢 Ad slot completely ended ($adState). Waiting 1.0s for ad bridge...")
                    isAdPlaying = false
                    adSkipCountdownJob?.cancel()
                    adPodBridgeJob?.cancel()
                    adPodBridgeJob = GlobalScope.launch(Dispatchers.Main) {
                        delay(1000)
                        if (!isAdPlaying && isAutoMuted) {
                            AppLogger.i(TAG, "📢 Ad slot finished confirmed. Restoring audio.")
                            restoreAudio()
                        }
                    }
                    return
                }

                // 2. 광고 진행 상태 설정 및 음소거 적용
                val adIsPlaying = adState == "1" || adState == "0" || adState == "-1" ||
                                  adState.equals("PLAYING", ignoreCase = true) ||
                                  eventType == "adPlaying" || adVideoId.isNotEmpty() ||
                                  isSkipEnabled.equals("true", ignoreCase = true)

                if (adIsPlaying) {
                    adPodBridgeJob?.cancel()
                    this.isAdPlaying = true
                    if (isAutoMuteEnabled()) {
                        muteAudio()
                    }
                    // 광고 시작 시 5초 카운트다운 종료 시점(5.2초, 6.4초, 7.9초) 자동 skipAd 예약!
                    scheduleAdSkipCountdownRetries()
                }

                // 3. 건너뛰기 가능 신호 수신 시 즉시 skipAd 전송 + 화면 우하단 스킵 병행
                if (isSkipEnabled.equals("true", ignoreCase = true)) {
                    AppLogger.i(TAG, "🔥 [Lounge] Ad skip enabled signal! Dispatching skipAd & screen click!")
                    sendSkipAdCommand()
                    try {
                        TvAdSkipService.instance?.triggerDirectScreenSkip()
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Failed to trigger direct screen skip", e)
                    }
                }
            }
            "onStateChange" -> {
                val state = payload?.optInt("state", -1) ?: -1
                AppLogger.d(TAG, "onStateChange: state=$state (isAdPlaying=$isAdPlaying, isAutoMuted=$isAutoMuted)")

                if (state == 5 || state == 0) {
                    AppLogger.i(TAG, "🎬 [StateChange=$state] Video stopped/cued. Resetting ad state & restoring audio.")
                    isAdPlaying = false
                    adSkipCountdownJob?.cancel()
                    adPodBridgeJob?.cancel()
                    adPodBridgeJob = null
                    if (isAutoMuted) {
                        restoreAudio()
                    }
                } else if (state == 1 && !isAdPlaying && isAutoMuted) {
                    AppLogger.i(TAG, "🎬 [StateChange=1] Main playback active. Ensuring audio restored.")
                    restoreAudio()
                }
            }
            "nowPlaying" -> {
                val videoId = payload?.optString("videoId", "") ?: ""
                val adState = payload?.optString("adState", "") ?: ""
                val adVideoId = payload?.optString("adVideoId", "") ?: ""
                val hasAdInPayload = adState.isNotEmpty() || adVideoId.isNotEmpty()

                AppLogger.d(TAG, "nowPlaying: videoId=$videoId, adState=$adState, adVideoId=$adVideoId (isAdPlaying=$isAdPlaying, isAutoMuted=$isAutoMuted)")

                if (hasAdInPayload || adState == "1") {
                    isAdPlaying = true
                    if (isAutoMuteEnabled()) {
                        adPodBridgeJob?.cancel()
                        muteAudio()
                    }
                    scheduleAdSkipCountdownRetries()
                } else if (videoId.isNotEmpty()) {
                    isAdPlaying = false
                    adSkipCountdownJob?.cancel()
                    adPodBridgeJob?.cancel()
                    adPodBridgeJob = null
                    if (isAutoMuted) {
                        AppLogger.i(TAG, "🎬 [NowPlaying] Pure main video confirmed ($videoId). Restoring audio immediately!")
                        restoreAudio()
                    }
                } else if (videoId.isEmpty() && isAutoMuted) {
                    AppLogger.i(TAG, "🎬 [NowPlaying] Empty videoId (Home/Back). Restoring audio.")
                    isAdPlaying = false
                    adSkipCountdownJob?.cancel()
                    adPodBridgeJob?.cancel()
                    adPodBridgeJob = null
                    restoreAudio()
                }
            }
            "loungeScreenDisconnected" -> {
                AppLogger.i(TAG, "Lounge screen disconnected. Clearing session & refreshing token on reconnect.")
                isConnected = false
                sid = null
                gsessionid = null
                isAdPlaying = false
                adSkipCountdownJob?.cancel()
                adPodBridgeJob?.cancel()
                restoreAudio()
                onStatusChanged?.invoke(false, "화면 연결 대기 중 (자동 재연결 중)")
            }
        }
    }

    /**
     * 광고 시작 후 5초 카운트다운이 끝나는 시점에 맞춰 자동으로 순수 skipAd 명령과 우하단 터치를 발사하는 타이머
     * (⚠️ 리모컨 ENTER는 우측 광고 상세 분할 화면을 열 수 있으므로 절대 사용하지 않고 skipAd 전용 명령만 발송)
     */
    private fun scheduleAdSkipCountdownRetries() {
        if (adSkipCountdownJob?.isActive == true) return

        adSkipCountdownJob = GlobalScope.launch(Dispatchers.IO) {
            val checkpointsMs = listOf(5200L, 1200L, 1500L, 2500L) // 누적: 5.2초, 6.4초, 7.9초, 10.4초
            for (waitMs in checkpointsMs) {
                delay(waitMs)
                if (!isAdPlaying || !isConnected) break
                AppLogger.i(TAG, "⏱️ [Ad-Countdown-Timer] Firing scheduled skipAd while ad is playing")
                val ok = sendCommand("skipAd")
                if (ok) {
                    onSkipCommandSucceeded()
                }
                try {
                    TvAdSkipService.instance?.triggerDirectScreenSkip()
                } catch (_: Exception) {}
            }
        }
    }

    private fun onSkipCommandSucceeded() {
        val count = prefs.getInt(PREFS_KEY_LOUNGE_SKIP_COUNT, 0) + 1
        prefs.edit()
            .putInt(PREFS_KEY_LOUNGE_SKIP_COUNT, count)
            .putLong(PREFS_KEY_LAST_SKIP_TIME, System.currentTimeMillis())
            .apply()
        onAdSkippedCallback?.invoke(count)
    }

    private fun muteAudio() {
        if (isAutoMuted) return
        if (System.currentTimeMillis() - lastSkipAdTime < 2500L) {
            AppLogger.d(TAG, "Mute blocked: within 2.5s after skipAd to protect main video audio.")
            return
        }
        isAutoMuted = true
        adStartTime = System.currentTimeMillis()

        if (currentVolume > 0) {
            savedVolumeBeforeMute = currentVolume
        }

        GlobalScope.launch(Dispatchers.IO) {
            val success = sendSetVolumeCommand(0)
            AppLogger.i(TAG, "🔇 [YouTube-Mute] YouTube player volume set to 0 (saved: $savedVolumeBeforeMute, success=$success)")
        }

        onAutoMuteCallback?.invoke(true, 0)

        muteTimeoutJob?.cancel()
        muteTimeoutJob = GlobalScope.launch(Dispatchers.Main) {
            delay(35000)
            if (isAutoMuted) {
                AppLogger.w(TAG, "⏰ [Safety Timeout] Auto-restoring YouTube volume after 35s timeout!")
                isAdPlaying = false
                restoreAudio()
            }
        }
    }

    private fun restoreAudio() {
        muteTimeoutJob?.cancel()
        muteTimeoutJob = null
        adPodBridgeJob?.cancel()
        adPodBridgeJob = null

        if (!isAutoMuted) return

        isAutoMuted = false
        isAdPlaying = false

        val restoreVol = if (savedVolumeBeforeMute in 10..100) savedVolumeBeforeMute else 100

        GlobalScope.launch(Dispatchers.IO) {
            val s1 = sendSetVolumeCommand(restoreVol)
            AppLogger.i(TAG, "🔊 [YouTube-Restore-1] Volume set to $restoreVol% (success=$s1)")
            delay(300)
            val s2 = sendSetVolumeCommand(restoreVol)
            AppLogger.i(TAG, "🔊 [YouTube-Restore-2] Volume confirmed at $restoreVol% (success=$s2)")
        }

        onAutoMuteCallback?.invoke(false, restoreVol)
    }

    fun forceRestoreAudio() {
        try {
            audioManager?.let { am ->
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (curVol < 10) {
                    val targetVol = if (maxVol >= 10) 10 else maxVol
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                    }
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                    AppLogger.i(TAG, "🔊 [TV-SysVol-Recover] Recovered TV system volume from $curVol to $targetVol (max=$maxVol)")
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "TV system volume recovery failed", e)
        }

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
        lastSkipAdTime = System.currentTimeMillis()

        GlobalScope.launch(Dispatchers.IO) {
            val s1 = sendCommand("skipAd")
            AppLogger.i(TAG, "🎯 [Lounge-1] Sent skipAd attempt 1 (success=$s1)")
            delay(400)
            val s2 = sendCommand("skipAd")
            AppLogger.i(TAG, "🎯 [Lounge-2] Sent skipAd attempt 2 (success=$s2)")

            val success = s1 || s2
            if (success) {
                onSkipCommandSucceeded()
                AppLogger.i(TAG, "🎯 [Lounge] Successfully executed double-tap skipAd on TV!")

                delay(1500)
                if (!isAdPlaying && isAutoMuted) {
                    AppLogger.i(TAG, "🎯 [Lounge] Post-skip ad confirmed ended. Restoring audio.")
                    restoreAudio()
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
