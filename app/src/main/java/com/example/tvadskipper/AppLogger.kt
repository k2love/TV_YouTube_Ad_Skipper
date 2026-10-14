package com.example.tvadskipper

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object AppLogger {


    private const val LOG_FILE_NAME = "ad_skipper_runtime.log"
    private const val MAX_LOG_BYTES = 2 * 1024 * 1024 // 최대 2MB 유지
    private val logExecutor = Executors.newSingleThreadExecutor()
    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
        i("AppLogger", "=== TvAdSkipper Logger Initialized ===")
    }

    /**
     * 메인 UI 스레드 블로킹(버벅임) 방지를 위해 모든 디스크 파일 쓰기를 단일 백그라운드 스레드에서 비동기 처리
     */
    private fun enqueueWriteToFile(level: String, tag: String, message: String) {
        val file = logFile ?: return
        val timestampMs = System.currentTimeMillis()
        logExecutor.execute {
            try {
                if (file.exists() && file.length() > MAX_LOG_BYTES) {
                    file.delete()
                }
                val sdf = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.KOREA)
                val time = sdf.format(Date(timestampMs))
                val line = "$time [$level/$tag] $message\n"
                FileOutputStream(file, true).use { fos ->
                    fos.write(line.toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                Log.e("AppLogger", "Failed to write log to file", e)
            }
        }
    }

    fun d(tag: String, message: String) {
        // 빈번한 디버그 로그는 Logcat에만 출력하고 플래시 스토리지 I/O는 생략하여 시스템 부하 최소화
        Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        enqueueWriteToFile("I", tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val extra = if (throwable != null) " | Exception: ${throwable.message}" else ""
        Log.w(tag, message + extra, throwable)
        enqueueWriteToFile("W", tag, message + extra)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val extra = if (throwable != null) " | Exception: ${throwable.message}" else ""
        Log.e(tag, message + extra, throwable)
        enqueueWriteToFile("E", tag, message + extra)
    }

    fun getLogFile(): File? = logFile

    fun getRecentLogs(maxLines: Int = 100): String {
        val file = logFile ?: return "로그 파일이 초기화되지 않았습니다."
        if (!file.exists()) return "기록된 로그가 아직 없습니다."
        return try {
            val lines = file.readLines(Charsets.UTF_8)
            lines.takeLast(maxLines).joinToString("\n")
        } catch (e: Exception) {
            "로그 읽기 실패: ${e.message}"
        }
    }

    fun clearLogs() {
        logExecutor.execute {
            try {
                logFile?.delete()
                enqueueWriteToFile("I", "AppLogger", "=== Logs Cleared ===")
            } catch (e: Exception) {
                Log.e("AppLogger", "Failed to clear logs", e)
            }
        }
    }
}

