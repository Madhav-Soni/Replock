package com.fitness.squatcounter.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.fitness.squatcounter.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpdateAvailable(val updateInfo: AppUpdateInfo) : UpdateState
    data class Downloading(val progressPercent: Int = -1) : UpdateState
    data class ReadyToInstall(val apkFile: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

class AppUpdateManager(private val context: Context) {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var downloadId: Long = -1

    suspend fun checkForUpdates(
        owner: String = "Madhav-Soni",
        repo: String = "Replock"
    ) = withContext(Dispatchers.IO) {
        try {
            _updateState.value = UpdateState.Checking

            // First check version.json directly from repository raw content (never rate-limited)
            val rawJsonUrl = "https://raw.githubusercontent.com/$owner/$repo/main/version.json"
            val rawResponse = fetchHttpText(rawJsonUrl)

            if (rawResponse != null) {
                val json = JSONObject(rawResponse)
                val remoteVersionCode = json.optInt("versionCode", 1)
                val versionName = json.optString("versionName", "v$remoteVersionCode")
                val downloadUrl = json.optString("downloadUrl", "")
                val releaseNotes = json.optString("releaseNotes", "New update available!")

                val currentVersionCode = BuildConfig.VERSION_CODE
                Log.d("AppUpdateManager", "Raw check: current=$currentVersionCode, remote=$remoteVersionCode")

                if (remoteVersionCode > currentVersionCode && downloadUrl.isNotBlank()) {
                    _updateState.value = UpdateState.UpdateAvailable(
                        AppUpdateInfo(
                            latestVersionCode = remoteVersionCode,
                            latestVersionName = versionName,
                            downloadUrl = downloadUrl,
                            releaseNotes = releaseNotes
                        )
                    )
                    return@withContext
                }
            }

            // Fallback to GitHub Releases API
            val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val apiResponse = fetchHttpText(apiUrl, isApi = true)

            if (apiResponse != null) {
                val json = JSONObject(apiResponse)
                val tagName = json.optString("tag_name", "")
                val body = json.optString("body", "Bug fixes and improvements.")
                val assets = json.optJSONArray("assets")

                var downloadUrl = ""
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                val remoteVersionCode = parseVersionCode(tagName)
                val currentVersionCode = BuildConfig.VERSION_CODE

                if (remoteVersionCode > currentVersionCode && downloadUrl.isNotBlank()) {
                    _updateState.value = UpdateState.UpdateAvailable(
                        AppUpdateInfo(
                            latestVersionCode = remoteVersionCode,
                            latestVersionName = tagName,
                            downloadUrl = downloadUrl,
                            releaseNotes = body
                        )
                    )
                    return@withContext
                }
            }

            _updateState.value = UpdateState.Idle
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "Failed to check update", e)
            _updateState.value = UpdateState.Idle
        }
    }

    private fun fetchHttpText(urlString: String, isApi: Boolean = false): String? {
        return try {
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                if (isApi) {
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                }
                setRequestProperty("User-Agent", "RepLock-App")
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use(BufferedReader::readText)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseVersionCode(tag: String): Int {
        val digitsOnly = tag.filter { it.isDigit() }
        return digitsOnly.toIntOrNull() ?: 1
    }

    fun startDownload(updateInfo: AppUpdateInfo) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (downloadManager == null) {
            _updateState.value = UpdateState.Error("Download Manager unavailable")
            return
        }

        _updateState.value = UpdateState.Downloading()

        val fileName = "RepLock-update-${updateInfo.latestVersionCode}.apk"
        val destinationFile = File(context.getExternalFilesDir(null), fileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(updateInfo.downloadUrl))
            .setTitle("RepLock Update")
            .setDescription("Downloading latest version ${updateInfo.latestVersionName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destinationFile))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                if (id == downloadId) {
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Exception) {}
                    _updateState.value = UpdateState.ReadyToInstall(destinationFile)
                    installApk(destinationFile)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    fun installApk(apkFile: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "Failed to launch installer", e)
            _updateState.value = UpdateState.Error("Cannot start installer: ${e.localizedMessage}")
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }
}

