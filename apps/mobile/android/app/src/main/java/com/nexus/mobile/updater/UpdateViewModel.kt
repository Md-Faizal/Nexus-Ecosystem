package com.nexus.mobile.updater

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject

data class ReleaseManifestInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val apkUrl: String,
    val sha256: String,
    val publishedAt: String,
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val currentInfo: Pair<Int, String> = run {
        val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode.toInt() else @Suppress("DEPRECATION") pInfo.versionCode
        code to (pInfo.versionName ?: "2.0.0")
    }

    val currentVersionCode: Int get() = currentInfo.first
    val currentVersionName: String get() = currentInfo.second

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.UpToDate(currentVersionName))
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private val _hasUpdateAvailable = MutableStateFlow(false)
    val hasUpdateAvailable: StateFlow<Boolean> = _hasUpdateAvailable.asStateFlow()

    private var latestManifest: ReleaseManifestInfo? = null

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UpdateUiState.Checking
            try {
                val url = URL("https://faizal.uk/releases/mobile-latest.json?t=${System.currentTimeMillis()}")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                val code = conn.responseCode
                if (code != 200) {
                    _uiState.value = UpdateUiState.Error("Manifest check failed (HTTP $code)")
                    return@launch
                }
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val manifest = ReleaseManifestInfo(
                    versionCode = json.optInt("version_code", 1),
                    versionName = json.optString("version_name", "2.0.0"),
                    releaseNotes = json.optString("release_notes", "Performance and security updates."),
                    apkUrl = json.optString("apk_url", ""),
                    sha256 = json.optString("sha256", ""),
                    publishedAt = json.optString("published_at", ""),
                )
                latestManifest = manifest
                if (manifest.versionCode > currentVersionCode && manifest.apkUrl.isNotBlank()) {
                    _hasUpdateAvailable.value = true
                    _uiState.value = UpdateUiState.Available(
                        versionName = manifest.versionName,
                        releaseNotes = manifest.releaseNotes,
                        sha256Preview = manifest.sha256.take(16),
                    )
                } else {
                    _hasUpdateAvailable.value = false
                    _uiState.value = UpdateUiState.UpToDate(currentVersionName)
                }
            } catch (e: Exception) {
                _uiState.value = UpdateUiState.Error(e.message ?: "Unable to reach faizal.uk")
            }
        }
    }

    fun downloadAndVerify() {
        val manifest = latestManifest ?: return
        if (manifest.sha256.isBlank() || manifest.sha256 == "pending-first-ci-build") {
            _uiState.value = UpdateUiState.Error("Release checksum is not published yet on server.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val tmpFile = File(updatesDir, "nexus-update.apk.tmp")
            val finalFile = File(updatesDir, "nexus-${manifest.versionName}.apk")

            try {
                val existingBytes = if (tmpFile.exists()) tmpFile.length() else 0L
                val conn = (URL(manifest.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    if (existingBytes > 0L) {
                        setRequestProperty("Range", "bytes=$existingBytes-")
                    }
                }
                conn.connect()
                val status = conn.responseCode
                if (status != 200 && status != 206) {
                    _uiState.value = UpdateUiState.Error("Download failed (HTTP $status)")
                    return@launch
                }

                val isPartial = status == 206
                val resumeOffset = if (isPartial) existingBytes else 0L
                val contentLength = conn.contentLengthLong
                val totalBytes = if (contentLength > 0L) resumeOffset + contentLength else 0L

                var downloaded = resumeOffset
                val startNs = System.nanoTime()
                var lastEmitMs = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(tmpFile, isPartial).use { out ->
                        val buf = ByteArray(16_384)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            downloaded += n

                            val nowMs = System.currentTimeMillis()
                            if (nowMs - lastEmitMs >= 150L || (totalBytes > 0L && downloaded >= totalBytes)) {
                                val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.1)
                                val bytesPerSec = ((downloaded - resumeOffset) / elapsedSec).toLong().coerceAtLeast(1L)
                                val etaSec = if (totalBytes > downloaded) (totalBytes - downloaded) / bytesPerSec else 0L
                                val fraction = if (totalBytes > 0L) downloaded.toFloat() / totalBytes.toFloat() else 0f
                                _uiState.value = UpdateUiState.Downloading(
                                    versionName = manifest.versionName,
                                    progressFraction = fraction,
                                    speedLabel = formatSpeed(bytesPerSec),
                                    etaLabel = "${etaSec}s",
                                )
                                lastEmitMs = nowMs
                            }
                        }
                    }
                }

                // Mandatory SHA-256 verification before permitting installation
                val actualSha = computeSha256(tmpFile)
                if (!actualSha.equals(manifest.sha256.trim(), ignoreCase = true)) {
                    tmpFile.delete()
                    _uiState.value = UpdateUiState.Error("SHA-256 mismatch — aborted for safety.")
                    return@launch
                }

                if (finalFile.exists()) finalFile.delete()
                tmpFile.renameTo(finalFile)
                _uiState.value = UpdateUiState.ReadyToInstall(
                    versionName = manifest.versionName,
                    apkPath = finalFile.absolutePath,
                )
            } catch (e: Exception) {
                _uiState.value = UpdateUiState.Error(e.message ?: "Download interrupted")
            }
        }
    }

    private fun computeSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(65_536)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        val mb = bytesPerSec / (1024.0 * 1024.0)
        return if (mb >= 1.0) "%.1f MB/s".format(mb) else "${bytesPerSec / 1024} KB/s"
    }
}
