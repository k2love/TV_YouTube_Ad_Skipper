package com.example.tvadskipper

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
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
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        Log.i(TAG, "LoungeSkipService created.")

        val loungeManager = LoungeManager.getInstance(this)
        loungeManager.onAdSkippedCallback = { count ->
            mainHandler.post {
                Toast.makeText(
                    applicationContext,
                    "🎯 [Lounge] 유튜브 광고 자동 건너뛰기 성공! (${count}회)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        loungeManager.onAutoMuteCallback = { isMuted, volume ->
            mainHandler.post {
                if (isMuted) {
                    Toast.makeText(
                        applicationContext,
                        "🔇 [Lounge] 강제 광고 감지: 하드웨어 자동 음소거 적용",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        applicationContext,
                        "🔊 [Lounge] 본 영상 재생: 원래 볼륨($volume) 복원",
                        Toast.LENGTH_SHORT
                    ).show()
                }
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
