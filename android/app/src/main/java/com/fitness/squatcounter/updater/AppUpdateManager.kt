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
            val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "RepLock-App")
            }

            if (connection.responseCode != 200) {
                _updateState.value = UpdateState.Idle
                return@withContext
            }

            val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val json = JSONObject(response)
            val tagName = json.optString("tag_name", "") // e.g. "v1.1" or "v2" or "2"
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

            // Extract numeric version from tag e.g. "v2" -> 2, "v1.2.0" -> 2
            val remoteVersionCode = parseVersionCode(tagName)
            val currentVersionCode = BuildConfig.VERSION_CODE

            Log.d("AppUpdateManager", "Current version: $currentVersionCode, Remote version: $remoteVersionCode")

            if (remoteVersionCode > currentVersionCode && downloadUrl.isNotBlank()) {
                val updateInfo = AppUpdateInfo(
                    latestVersionCode = remoteVersionCode,
                    latestVersionName = tagName,
                    downloadUrl = downloadUrl,
                    releaseNotes = body
                )
                _updateState.value = UpdateState.UpdateAvailable(updateInfo)
            } else {
                _updateState.value = UpdateState.Idle
            }
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "Failed to check update", e)
            _updateState.value = UpdateState.Idle
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

