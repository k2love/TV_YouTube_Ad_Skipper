package com.example.tvadskipper

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class UpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "UpdateManager"
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_LAST_KNOWN_MODIFIED = "key_last_known_modified"
        private const val KEY_LAST_KNOWN_SIZE = "key_last_known_size"
        private const val DEFAULT_APK_NAME = "TV_YouTube_Ad_Skipper.apk"

        // 하드코딩된 구글 드라이브 파일 ID 및 바이패스 직다운로드 주소
        const val HARDCODED_FILE_ID = "1MkTOnPgTiBzQ6BsZxXB9eyP9qBWusM-n"
        const val DIRECT_DOWNLOAD_URL = "https://drive.usercontent.google.com/download?id=$HARDCODED_FILE_ID&export=download&confirm=t"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            52
        }
    }

    fun getCurrentVersionName(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.5.2"
        } catch (e: Exception) {
            "0.5.2"
        }
    }

    /**
     * 원격 구글 드라이브 APK의 수정 시간과 현재 설치된 앱의 설치 시간을 비교하여 새 빌드 업로드 여부 감지
     */
    suspend fun checkRemoteApkUpdated(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Checking remote APK metadata from: $DIRECT_DOWNLOAD_URL")
            val request = Request.Builder()
                .url(DIRECT_DOWNLOAD_URL)
                .head()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val remoteLastModifiedStr = response.header("Last-Modified") ?: ""
            val remoteLengthStr = response.header("Content-Length") ?: ""

            // OkHttp 내장 HTTP 날짜 파서 활용
            val remoteDate = response.headers.getDate("Last-Modified")
            val remoteTimeMs = remoteDate?.time ?: 0L
            val remoteSize = remoteLengthStr.toLongOrNull() ?: 0L
            response.close()

            if (remoteLastModifiedStr.isEmpty() && remoteLengthStr.isEmpty()) {
                return@withContext Result.success(false)
            }

            // 현재 TV에 설치된 앱의 설치/업데이트 완료 시각
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val localInstalledTime = packageInfo.lastUpdateTime

            val savedModified = prefs.getString(KEY_LAST_KNOWN_MODIFIED, "") ?: ""
            val savedSize = prefs.getString(KEY_LAST_KNOWN_SIZE, "") ?: ""

            Log.i(TAG, "RemoteTime: $remoteTimeMs vs LocalInstallTime: $localInstalledTime | Saved: [$savedModified, $savedSize] vs Remote: [$remoteLastModifiedStr, $remoteLengthStr]")

            // 1. 구글 드라이브 파일의 수정 시각이 현재 설치된 앱의 설치 시각보다 60초 이상 최신인 경우 새 빌드로 판단!
            val isNewerThanInstall = (remoteTimeMs > 0 && localInstalledTime > 0) && (remoteTimeMs > localInstalledTime + 60_000L)

            // 2. 이전에 이미 다운로드하여 기록해둔 헤더와 비교 (새 파일 업로드로 헤더가 변경된 경우)
            val isHeaderChanged = savedModified.isNotEmpty() && (savedModified != remoteLastModifiedStr || (savedSize.isNotEmpty() && savedSize != remoteLengthStr))

            val isUpdated = isNewerThanInstall || isHeaderChanged

            // 현재 설치본이 최신 상태라면 헤더를 동기화하여 중복 감지 방지
            if (!isUpdated) {
                prefs.edit()
                    .putString(KEY_LAST_KNOWN_MODIFIED, remoteLastModifiedStr)
                    .putString(KEY_LAST_KNOWN_SIZE, remoteLengthStr)
                    .apply()
            }

            Result.success(isUpdated)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking remote APK update", e)
            Result.failure(e)
        }
    }

    /**
     * 구글 드라이브에서 최신 APK 직접 다운로드
     */
    suspend fun downloadApk(
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Downloading APK from: $DIRECT_DOWNLOAD_URL")
            val request = Request.Builder()
                .url(DIRECT_DOWNLOAD_URL)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("APK 다운로드 실패 (HTTP ${response.code})"))
            }

            val remoteLastModified = response.header("Last-Modified") ?: ""
            val remoteLength = response.header("Content-Length") ?: ""
            val body = response.body ?: return@withContext Result.failure(Exception("다운로드 데이터가 없습니다."))
            val contentLength = body.contentLength()

            val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val destFile = File(storageDir, DEFAULT_APK_NAME)
            if (destFile.exists()) {
                destFile.delete()
            }

            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(destFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (contentLength > 0) {
                    val progress = ((totalBytesRead * 100) / contentLength).toInt()
                    onProgress(progress)
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            destFile.setReadable(true, false)

            // 다운로드 성공 시 최신 헤더 저장
            prefs.edit()
                .putString(KEY_LAST_KNOWN_MODIFIED, remoteLastModified)
                .putString(KEY_LAST_KNOWN_SIZE, remoteLength)
                .apply()

            Log.i(TAG, "APK successfully downloaded to: ${destFile.absolutePath} (${destFile.length()} bytes)")
            AppLogger.i(TAG, "APK successfully downloaded to: ${destFile.absolutePath} (${destFile.length()} bytes)")
            Result.success(destFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK", e)
            Result.failure(e)
        }
    }

    /**
     * 다운로드된 APK 파일 정보 확인
     */
    fun getApkVersionInfo(apkFile: File): Pair<Int, String>? {
        return try {
            val packageInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            if (packageInfo != null) {
                val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }
                val name = packageInfo.versionName ?: "알 수 없음"
                Pair(code, name)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 안드로이드 시스템 패키지 인스톨러 호출
     */
    fun installApk(apkFile: File): Boolean {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                Log.e(TAG, "APK file does not exist or empty: ${apkFile.absolutePath}")
                AppLogger.e(TAG, "APK file does not exist or empty: ${apkFile.absolutePath}")
                return false
            }

            apkFile.setReadable(true, false)

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            // 모든 관련 PackageInstaller 패키지에 명시적 읽기 권한 부여
            val resInfoList = context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val pkgName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(pkgName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            Log.i(TAG, "Launching APK installation intent: $contentUri (${apkFile.length()} bytes)")
            AppLogger.i(TAG, "Launching APK installation intent: $contentUri (${apkFile.length()} bytes)")
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error launching install intent", e)
            AppLogger.e(TAG, "Error launching install intent: ${e.message}")
            false
        }
    }
}
