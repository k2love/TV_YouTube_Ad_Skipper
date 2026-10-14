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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class UpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "UpdateManager"
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_LAST_KNOWN_SHA = "key_last_known_sha"
        private const val KEY_LAST_KNOWN_ETAG = "key_last_known_etag"
        private const val KEY_LAST_KNOWN_SIZE = "key_last_known_size"
        private const val DEFAULT_APK_NAME = "TV_YouTube_Ad_Skipper.apk"

        // GitHub 저장소 정보 (https://github.com/k2love/TV_YouTube_Ad_Skipper)
        const val GITHUB_OWNER = "k2love"
        const val GITHUB_REPO = "TV_YouTube_Ad_Skipper"
        const val GITHUB_BRANCH = "main"

        // GitHub 루트 경로에 업로드된 TV_YouTube_Ad_Skipper.apk 직다운로드 주소
        const val GITHUB_RAW_APK_URL =
            "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/$GITHUB_BRANCH/$DEFAULT_APK_NAME"

        // GitHub APK 파일의 최신 커밋 시각 및 SHA 조회 API (version.json 수정 없이 APK만 올려도 감지!)
        const val GITHUB_APK_COMMITS_API_URL =
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/commits?path=$DEFAULT_APK_NAME&sha=$GITHUB_BRANCH&page=1&per_page=1"

        // 보조: version.json 및 GitHub Releases 주소
        const val GITHUB_VERSION_JSON_URL =
            "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/$GITHUB_BRANCH/version.json"
        const val GITHUB_RELEASE_LATEST_APK_URL =
            "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest/download/$DEFAULT_APK_NAME"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Volatile
    var latestVersionCode: Int = -1
        private set

    @Volatile
    var latestVersionName: String = ""
        private set

    @Volatile
    var latestChangelog: String = ""
        private set

    @Volatile
    var latestCommitSha: String = ""
        private set

    @Volatile
    var resolvedDownloadUrl: String = GITHUB_RAW_APK_URL
        private set

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
            65
        }
    }

    fun getCurrentVersionName(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.6.5"
        } catch (e: Exception) {
            "0.6.5"
        }
    }

    /**
     * GitHub에 올라온 'TV_YouTube_Ad_Skipper.apk'의 업로드(커밋) 시각/해시 및 version.json을 비교하여
     * 새 APK가 올라왔는지 자동 감지 (기존 구글 드라이브 방식과 100% 동일하게 APK 파일만 교체해도 감지됨)
     */
    suspend fun checkRemoteApkUpdated(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val currentCode = getCurrentVersionCode()
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val localInstalledTime = packageInfo.lastUpdateTime

            // 1. GitHub Commits API로 'TV_YouTube_Ad_Skipper.apk' 파일의 실제 업로드 시각 & 커밋 SHA 확인
            val commitsReq = Request.Builder()
                .url("$GITHUB_APK_COMMITS_API_URL&t=${System.currentTimeMillis()}")
                .get()
                .header("Accept", "application/vnd.github+json")
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "TV-YouTube-Ad-Skipper-Updater")
                .build()

            client.newCall(commitsReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: ""
                    if (bodyStr.isNotEmpty()) {
                        val arr = JSONArray(bodyStr)
                        if (arr.length() > 0) {
                            val commitEntry = arr.getJSONObject(0)
                            val sha = commitEntry.optString("sha", "")
                            val commitObj = commitEntry.optJSONObject("commit")
                            val message = commitObj?.optString("message", "") ?: ""
                            val dateStr = commitObj?.optJSONObject("committer")?.optString("date", "") ?: ""

                            val remoteTimeMs = parseIso8601ToMillis(dateStr)
                            val savedSha = prefs.getString(KEY_LAST_KNOWN_SHA, "") ?: ""

                            latestCommitSha = sha
                            if (message.isNotEmpty()) {
                                latestChangelog = message
                            }
                            resolvedDownloadUrl = GITHUB_RAW_APK_URL

                            // ① GitHub의 APK 커밋 시각이 현재 TV 앱 설치 시각보다 60초 이상 최신이거나
                            // ② 이전에 기록된 커밋 SHA와 다른 새 커밋이 올라온 경우 새 업데이트로 판정!
                            val isNewerThanInstall = (remoteTimeMs > 0 && localInstalledTime > 0) &&
                                    (remoteTimeMs > localInstalledTime + 60_000L)
                            val isShaChanged = savedSha.isNotEmpty() && sha.isNotEmpty() && savedSha != sha

                            Log.i(TAG, "GitHub APK Commit check: remoteTime=$remoteTimeMs vs localInstall=$localInstalledTime, sha=$sha vs savedSha=$savedSha")

                            if (isNewerThanInstall || isShaChanged) {
                                return@withContext Result.success(true)
                            }

                            if (savedSha.isEmpty() && sha.isNotEmpty()) {
                                prefs.edit().putString(KEY_LAST_KNOWN_SHA, sha).apply()
                            }
                        }
                    }
                }
            }

            // 2. 보조: GitHub version.json의 versionCode 비교
            val versionReq = Request.Builder()
                .url("$GITHUB_VERSION_JSON_URL?t=${System.currentTimeMillis()}")
                .get()
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "TV-YouTube-Ad-Skipper-Updater")
                .build()

            client.newCall(versionReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: ""
                    if (bodyStr.isNotEmpty()) {
                        val json = JSONObject(bodyStr)
                        val remoteCode = json.optInt("versionCode", -1)
                        val remoteName = json.optString("versionName", "")
                        val changelog = json.optString("changelog", "")

                        latestVersionCode = remoteCode
                        latestVersionName = remoteName
                        if (changelog.isNotEmpty()) {
                            latestChangelog = changelog
                        }

                        if (remoteCode > currentCode) {
                            resolvedDownloadUrl = GITHUB_RAW_APK_URL
                            return@withContext Result.success(true)
                        }
                    }
                }
            }

            // 3. 보조: GitHub Raw APK의 ETag / Content-Length 헤더 변경 확인
            val isHeaderChanged = checkRawApkHeaderChanged(GITHUB_RAW_APK_URL) ||
                    checkRawApkHeaderChanged(GITHUB_RELEASE_LATEST_APK_URL)

            Result.success(isHeaderChanged)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking GitHub APK update", e)
            Result.failure(e)
        }
    }

    private fun parseIso8601ToMillis(isoDate: String): Long {
        if (isoDate.isEmpty()) return 0L
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.parse(isoDate)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun checkRawApkHeaderChanged(apkUrl: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("$apkUrl?t=${System.currentTimeMillis()}")
                .head()
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "TV-YouTube-Ad-Skipper-Updater")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false

                val remoteEtag = response.header("ETag") ?: response.header("Last-Modified") ?: ""
                val remoteLength = response.header("Content-Length") ?: ""
                if (remoteEtag.isEmpty() && remoteLength.isEmpty()) return false

                val savedEtag = prefs.getString(KEY_LAST_KNOWN_ETAG, "") ?: ""
                val savedSize = prefs.getString(KEY_LAST_KNOWN_SIZE, "") ?: ""

                val isChanged = savedEtag.isNotEmpty() &&
                        (savedEtag != remoteEtag || (savedSize.isNotEmpty() && savedSize != remoteLength))

                if (isChanged) {
                    resolvedDownloadUrl = apkUrl
                    return true
                }

                if (savedEtag.isEmpty()) {
                    prefs.edit()
                        .putString(KEY_LAST_KNOWN_ETAG, remoteEtag)
                        .putString(KEY_LAST_KNOWN_SIZE, remoteLength)
                        .apply()
                }
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * GitHub에서 최신 TV_YouTube_Ad_Skipper.apk 직접 다운로드
     */
    suspend fun downloadApk(
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val candidateUrls = linkedSetOf(
            "$GITHUB_RAW_APK_URL?t=${System.currentTimeMillis()}",
            GITHUB_RELEASE_LATEST_APK_URL
        )

        var lastError: Exception? = null

        for (targetUrl in candidateUrls) {
            try {
                Log.i(TAG, "Downloading APK from GitHub: $targetUrl")
                val request = Request.Builder()
                    .url(targetUrl)
                    .get()
                    .header("Cache-Control", "no-cache")
                    .header("User-Agent", "TV-YouTube-Ad-Skipper-Updater")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    lastError = Exception("HTTP ${response.code}")
                    continue
                }

                val remoteEtag = response.header("ETag") ?: response.header("Last-Modified") ?: ""
                val remoteLength = response.header("Content-Length") ?: ""
                val body = response.body
                if (body == null) {
                    response.close()
                    lastError = Exception("다운로드 응답 데이터가 없습니다.")
                    continue
                }

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
                response.close()

                if (destFile.length() < 100 * 1024) {
                    destFile.delete()
                    lastError = Exception("다운로드된 파일 크기가 비정상입니다 (${totalBytesRead} bytes).")
                    continue
                }

                destFile.setReadable(true, false)

                prefs.edit().apply {
                    if (latestCommitSha.isNotEmpty()) putString(KEY_LAST_KNOWN_SHA, latestCommitSha)
                    if (remoteEtag.isNotEmpty()) putString(KEY_LAST_KNOWN_ETAG, remoteEtag)
                    if (remoteLength.isNotEmpty()) putString(KEY_LAST_KNOWN_SIZE, remoteLength)
                    apply()
                }

                Log.i(TAG, "APK successfully downloaded from GitHub: ${destFile.absolutePath} (${destFile.length()} bytes)")
                AppLogger.i(TAG, "APK successfully downloaded from GitHub: ${destFile.absolutePath} (${destFile.length()} bytes)")
                return@withContext Result.success(destFile)
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading APK from $targetUrl", e)
                lastError = e
            }
        }

        Result.failure(lastError ?: Exception("GitHub 저장소 루트에 TV_YouTube_Ad_Skipper.apk 파일이 업로드되어 있는지 확인해 주세요."))
    }

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
