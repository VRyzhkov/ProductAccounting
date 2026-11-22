package ru.productaccounting.update

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val manager = app.getSystemService(DownloadManager::class.java)
    private val preferences = app.getSharedPreferences("app_update", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(UpdateDownloadState())
    val state = mutableState.asStateFlow()
    private var downloadId = preferences.getLong("download_id", -1)
    private var watchJob: Job? = null

    init {
        val currentVersion = app.packageManager.getPackageInfo(app.packageName, 0)
            .versionName.orEmpty().removePrefix("v")
        val savedVersion = preferences.getString("version", null)
        if (downloadId != -1L && savedVersion != null && savedVersion != currentVersion) {
            mutableState.value = UpdateDownloadState(
                UpdatePhase.Downloading,
                AvailableUpdate(savedVersion, preferences.getString("url", "").orEmpty(), ""),
                "Проверка скачанного обновления…",
            )
            watchDownload()
        } else {
            clearDownload()
            viewModelScope.launch {
                try {
                    GitHubUpdateChecker().findUpdate(currentVersion)?.let {
                        mutableState.value = UpdateDownloadState(UpdatePhase.Offer, it)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // An unavailable update server must not prevent accounting work.
                }
            }
        }
    }

    fun download() {
        val update = state.value.update ?: return
        if (state.value.phase == UpdatePhase.Downloading) return
        clearDownload()
        try {
            val fileName = "update-${System.currentTimeMillis()}.apk"
            val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
                .setTitle("ProductAccounting ${update.versionName}")
                .setDescription("Обновление приложения. После загрузки вернитесь в ProductAccounting для установки.")
                .setMimeType(APK_MIME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(
                    app,
                    Environment.DIRECTORY_DOWNLOADS,
                    "updates/$fileName",
                )
            downloadId = manager.enqueue(request)
            preferences.edit()
                .putLong("download_id", downloadId)
                .putString("file_name", fileName)
                .putString("version", update.versionName)
                .putString("url", update.downloadUrl)
                .apply()
            mutableState.value = UpdateDownloadState(
                UpdatePhase.Downloading,
                update,
                "Начинается загрузка…",
            )
            watchDownload()
        } catch (_: Exception) {
            showError("Не удалось начать загрузку. Проверьте системный менеджер загрузок и свободное место.")
        }
    }

    fun dismiss() {
        if (state.value.phase == UpdatePhase.Downloading || state.value.phase == UpdatePhase.Failed) {
            clearDownload()
        }
        mutableState.value = UpdateDownloadState()
    }

    fun showInstallError(message: String) {
        mutableState.value = state.value.copy(message = message)
    }

    fun installerIntent(): Intent {
        check(state.value.phase == UpdatePhase.Ready) { "Обновление ещё не скачано" }
        val file = downloadedFile()
        check(file.isFile && file.length() > 0) {
            "Скачанный APK удалён. Закройте окно и скачайте обновление заново."
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun watchDownload() {
        watchJob?.cancel()
        val id = downloadId
        watchJob = viewModelScope.launch {
            try {
                while (true) {
                    val (phase, message) = withContext(Dispatchers.IO) {
                        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                            check(cursor != null && cursor.moveToFirst()) {
                                "Загрузка удалена. Скачайте обновление ещё раз."
                            }
                            val result = downloadStatus(
                                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                            )
                            if (result.first == UpdatePhase.Ready) validateDownloadedApk()
                            result
                        }
                    }
                    mutableState.value = state.value.copy(phase = phase, message = message)
                    if (phase != UpdatePhase.Downloading) break
                    delay(1_000)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showError(error.message ?: "Не удалось проверить загрузку. Повторите попытку.")
            }
        }
    }

    private fun validateDownloadedApk() {
        val file = downloadedFile()
        check(file.isFile && file.length() > 0) { "Файл обновления отсутствует. Скачайте его ещё раз." }
        val info = app.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        check(info?.packageName == app.packageName) {
            "Скачанный файл не является APK ProductAccounting. Повторите загрузку."
        }
    }

    private fun downloadedFile(): File {
        val root = requireNotNull(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        val name = requireNotNull(preferences.getString("file_name", null))
        check(name.matches(Regex("update-[0-9]+\\.apk"))) { "Неизвестный файл обновления" }
        return File(root, "updates/$name")
    }

    private fun showError(message: String) {
        mutableState.value = state.value.copy(phase = UpdatePhase.Failed, message = message)
    }

    private fun clearDownload() {
        watchJob?.cancel()
        if (downloadId != -1L) runCatching { manager.remove(downloadId) }
        downloadId = -1L
        preferences.edit().clear().apply()
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
