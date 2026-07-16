package com.termfast.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val assets: List<Asset> = emptyList(),
) {
    @Serializable
    data class Asset(
        val name: String,
        val browser_download_url: String,
        val size: Long,
        val browser_download_url_alt: String? = null,
    )
}

class GitHubReleaseUpdater(
    private val context: Context,
    private val owner: String = "termfast",
    private val repo: String = "ssh-proxy",
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchLatest(): GitHubRelease? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.inputStream.bufferedReader().use { r ->
                json.decodeFromString<GitHubRelease>(r.readText())
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun downloadApk(url: String, onProgress: (Float) -> Unit = {}): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.externalCacheDir, "updates").apply { mkdirs() }
            val outFile = File(dir, "termfast-update.apk")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 30_000
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                outFile.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    var read: Int
                    var downloaded = 0L
                    while (true) {
                        read = input.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                        downloaded += read
                        if (total > 0) onProgress(downloaded.toFloat() / total)
                    }
                }
            }
            outFile
        } catch (e: Exception) {
            null
        }
    }

    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    fun isNewer(remote: GitHubRelease, localVersion: String): Boolean {
        // tag_name like "v0.1.0"
        val remoteVer = remote.tag_name.removePrefix("v")
        return remoteVer != localVersion && remoteVer.isNotEmpty()
    }
}
