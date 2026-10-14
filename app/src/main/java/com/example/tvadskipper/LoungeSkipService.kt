package com.example.tvadskipper

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LoungeSkipService : Service() {

    companion object {
        private const val TAG = "LoungeSkipService"
        @Volatile
        var isServiceRunning = false
            private set
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var listeningJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        Log.i(TAG, "LoungeSkipService created.")

        // 🎯 순수 무음 모드: 영상 시청을 방해하는 하단 중앙 Toast 알림을 전면 배제하고 순수 백그라운드 처리
        val loungeManager = LoungeManager.getInstance(this)
        loungeManager.onAdSkippedCallback = { count ->
            Log.i(TAG, "🎯 [Lounge] Ad skipped silently (boot count: $count)")
            AppLogger.i(TAG, "🎯 [Lounge] Ad skipped silently (boot count: $count)")
        }

        loungeManager.onAutoMuteCallback = { isMuted, volume ->
            if (isMuted) {
                Log.i(TAG, "🔇 [Lounge] Auto-mute active")
                AppLogger.i(TAG, "🔇 [Lounge] Auto-mute active")
            } else {
                Log.i(TAG, "🔊 [Lounge] Main video playing: volume($volume) restored")
                AppLogger.i(TAG, "🔊 [Lounge] Main video playing: volume($volume) restored")
            }
        }

        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        if (listeningJob?.isActive == true) return

        listeningJob = serviceScope.launch {
            Log.i(TAG, "Starting Lounge monitoring loop...")
            LoungeManager.getInstance(this@LoungeSkipService).startListeningLoop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        listeningJob?.cancel()
        serviceScope.cancel()
        LoungeManager.getInstance(this).disconnect()
        Log.i(TAG, "LoungeSkipService destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
