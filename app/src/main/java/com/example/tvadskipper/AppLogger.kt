package com.example.tvadskipper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object AppLogger {

    const val GOOGLE_APPS_SCRIPT_URL = "https://script.google.com/macros/s/AKfycbzaftyrLBZc0yraJW-TWAGTdr0DIT0gdT3ODx0ob7O9e0UZZMmGu-JI3MV-B2Se4FKLBQ/exec"

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

    suspend fun uploadLogsToGoogleDrive(): Result<String> = withContext(Dispatchers.IO) {
        val file = logFile ?: return@withContext Result.failure(Exception("로그 파일이 초기화되지 않았습니다."))
        if (!file.exists() || file.length() == 0L) {
            return@withContext Result.failure(Exception("업로드할 로그 내용이 없습니다."))
        }

        try {
            val content = file.readText(Charsets.UTF_8)
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val mediaType = "text/plain; charset=utf-8".toMediaTypeOrNull()
            val requestBody = content.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(GOOGLE_APPS_SCRIPT_URL)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (response.isSuccessful || bodyString.contains("success")) {
                i("AppLogger", "Logs successfully uploaded to Google Drive Spreadsheet!")
                Result.success(bodyString)
            } else {
                w("AppLogger", "Log upload response not successful: HTTP ${response.code} $bodyString")
                Result.failure(Exception("구글 드라이브 응답 오류 (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            e("AppLogger", "Failed to upload logs to Google Drive", e)
            Result.failure(e)
        }
    }
}
