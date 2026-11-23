package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

data class RemoteAppVersion(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String = "",
    val publishedAt: String = ""
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(
        val currentVersionName: String,
        val currentVersionCode: Int
    ) : AppUpdateState

    data class UpdateAvailable(
        val remote: RemoteAppVersion,
        val currentVersionName: String,
        val currentVersionCode: Int
    ) : AppUpdateState

    data class Downloading(
        val remote: RemoteAppVersion,
        val progress: Float?,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : AppUpdateState

    data class PermissionRequired(
        val remote: RemoteAppVersion,
        val apkFile: File
    ) : AppUpdateState

    data class ReadyToInstall(
        val remote: RemoteAppVersion,
        val apkFile: File
    ) : AppUpdateState

    data class Error(
        val message: String,
        val remote: RemoteAppVersion? = null,
        val isSilent: Boolean = false
    ) : AppUpdateState
}

interface UpdateSnoozeStore {
    fun getDismissed(): Pair<Int, Long>?
    fun saveDismissed(versionCode: Int, timestampMs: Long)
}

object AppUpdateConfig {
    val VERSION_CHECK_URLS = listOf(
        "https://github.com/Zumdewisk/NYNYNY/releases/download/latest/version.json",
        "https://zumdewisk.github.io/NYNYNY/version.json"
    )

    val ALLOWED_HOSTS = setOf(
        "github.com",
        "zumdewisk.github.io",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "raw.githubusercontent.com"
    )

    const val PREFS_NAME = "zevra_app_update_prefs"
    const val KEY_DISMISSED_VERSION_CODE = "dismissed_version_code"
    const val KEY_DISMISSED_AT_MS = "dismissed_at_ms"
    const val SNOOZE_DURATION_MS = 24L * 60L * 60L * 1000L
    const val UPDATES_SUBDIR = "updates"
    const val UPDATE_APK_FILENAME = "Zevra-update.apk"
    const val MIN_VALID_APK_BYTES = 500_000L
}

object AppUpdateVersionComparator {
    private val moshi: Moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    fun isAllowedUpdateHost(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return false
        return try {
            val host = URI(trimmed).host?.lowercase().orEmpty()
            host in AppUpdateConfig.ALLOWED_HOSTS ||
                host.endsWith(".githubusercontent.com")
        } catch (_: Exception) {
            false
        }
    }

    fun parseVersionJson(rawJson: String): RemoteAppVersion? {
        return try {
            val map = mapAdapter.fromJson(rawJson.trim()) ?: return null
            val rawCode = map["versionCode"]
            val versionCode = when (rawCode) {
                is Number -> rawCode.toInt()
                is String -> rawCode.trim().toIntOrNull() ?: 0
                else -> 0
            }
            val versionName = (map["versionName"] as? String)?.trim().orEmpty()
            val apkUrl = (map["apkUrl"] as? String)?.trim().orEmpty()
            val releaseNotes = (map["releaseNotes"] as? String)?.trim().orEmpty()
            val publishedAt = (map["publishedAt"] as? String)?.trim().orEmpty()

            if (versionCode <= 0 || versionName.isBlank() || !isAllowedUpdateHost(apkUrl)) {
                return null
            }

            RemoteAppVersion(
                versionCode = versionCode,
                versionName = versionName,
                apkUrl = apkUrl,
                releaseNotes = releaseNotes,
                publishedAt = publishedAt
            )
        } catch (_: Exception) {
            null
        }
    }

    fun compareSemanticVersions(remote: String, current: String): Int {
        val remoteParts = remote.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '_')
            .mapNotNull { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() }
        val currentParts = current.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '_')
            .mapNotNull { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r.compareTo(c)
        }
        return 0
    }

    fun isNewerVersion(
        remote: RemoteAppVersion,
        currentVersionCode: Int,
        currentVersionName: String
    ): Boolean {
        if (remote.versionCode != currentVersionCode) {
            return remote.versionCode > currentVersionCode
        }
        return compareSemanticVersions(remote.versionName, currentVersionName) > 0
    }

    fun hasValidApkMagicHeader(header: ByteArray): Boolean {
        return header.size >= 4 &&
            header[0] == 'P'.code.toByte() &&
            header[1] == 'K'.code.toByte() &&
            header[2] == 0x03.toByte() &&
            header[3] == 0x04.toByte()
    }
}

class AppUpdateManager internal constructor(
    private val httpClient: OkHttpClient,
    private val versionUrls: List<String>,
    private val currentVersionCode: Int,
    private val currentVersionName: String,
    private val minValidApkBytes: Long,
    private val nowProvider: () -> Long,
    private val canInstallProvider: () -> Boolean,
    private val snoozeStore: UpdateSnoozeStore,
    private val updatesDirProvider: () -> File,
    private val archiveVerifier: (File) -> Unit,
    private val installerLauncher: (File) -> Boolean,
    private val installIntentFactory: (File) -> Intent?,
    private val unknownSourcesIntentFactory: () -> Intent?,
    private val mainDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val emitLog: (String, Throwable?) -> Unit
) {
    constructor(context: Context) : this(
        context = context,
        httpClient = defaultHttpClient()
    )

    internal constructor(
        context: Context,
        httpClient: OkHttpClient = defaultHttpClient(),
        versionUrls: List<String> = AppUpdateConfig.VERSION_CHECK_URLS,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        minValidApkBytes: Long = AppUpdateConfig.MIN_VALID_APK_BYTES,
        nowProvider: () -> Long = { System.currentTimeMillis() },
        canInstallProvider: () -> Boolean = {
            context.applicationContext.packageManager.canRequestPackageInstalls()
        },
        mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        httpClient = httpClient,
        versionUrls = versionUrls,
        currentVersionCode = currentVersionCode,
        currentVersionName = currentVersionName,
        minValidApkBytes = minValidApkBytes,
        nowProvider = nowProvider,
        canInstallProvider = canInstallProvider,
        snoozeStore = SharedPrefsSnoozeStore(context.applicationContext),
        updatesDirProvider = { File(context.applicationContext.cacheDir, AppUpdateConfig.UPDATES_SUBDIR) },
        archiveVerifier = { file -> verifyApkPackageArchive(context.applicationContext, file) },
        installerLauncher = { file -> launchAndroidInstaller(context.applicationContext, file) },
        installIntentFactory = { file -> createInstallIntent(context.applicationContext, file) },
        unknownSourcesIntentFactory = { createUnknownSourcesSettingsIntent(context.applicationContext) },
        mainDispatcher = mainDispatcher,
        ioDispatcher = ioDispatcher,
        emitLog = { msg, err -> if (err != null) Log.e(TAG, msg, err) else Log.w(TAG, msg) }
    )

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    private var hasRunAutomaticCheck = false
    private var downloadJob: Job? = null

    fun checkForUpdates(manual: Boolean = false) {
        if (!manual && hasRunAutomaticCheck) return
        if (_state.value is AppUpdateState.Downloading || _state.value is AppUpdateState.Checking) {
            if (manual) _showDialog.value = true
            return
        }
        if (!manual) {
            hasRunAutomaticCheck = true
        } else {
            _showDialog.value = true
        }

        _state.value = AppUpdateState.Checking

        scope.launch {
            val result = withContext(ioDispatcher) { fetchLatestRemoteVersion() }
            if (result == null) {
                if (manual) {
                    _state.value = AppUpdateState.Error(
                        message = "Kunde inte nå uppdateringsservern. Kontrollera din internetanslutning och försök igen.",
                        remote = null,
                        isSilent = false
                    )
                    _showDialog.value = true
                } else {
                    _state.value = AppUpdateState.Idle
                }
                return@launch
            }

            if (AppUpdateVersionComparator.isNewerVersion(result, currentVersionCode, currentVersionName)) {
                if (!manual && isSnoozed(result.versionCode)) {
                    _state.value = AppUpdateState.Idle
                    return@launch
                }
                _state.value = AppUpdateState.UpdateAvailable(
                    remote = result,
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode
                )
                _showDialog.value = true
            } else {
                _state.value = if (manual) {
                    _showDialog.value = true
                    AppUpdateState.UpToDate(
                        currentVersionName = currentVersionName,
                        currentVersionCode = currentVersionCode
                    )
                } else {
                    AppUpdateState.Idle
                }
            }
        }
    }

    fun startDownload(remote: RemoteAppVersion) {
        downloadJob?.cancel()
        _showDialog.value = true
        _state.value = AppUpdateState.Downloading(
            remote = remote,
            progress = null,
            bytesDownloaded = 0L,
            totalBytes = -1L
        )

        downloadJob = scope.launch {
            try {
                val apkFile = withContext(ioDispatcher) {
                    downloadAndVerifyApk(remote)
                }

                if (canInstallProvider()) {
                    _state.value = AppUpdateState.ReadyToInstall(remote, apkFile)
                    launchApkInstaller(apkFile)
                } else {
                    _state.value = AppUpdateState.PermissionRequired(remote, apkFile)
                }
            } catch (_: CancellationException) {
                _state.value = AppUpdateState.UpdateAvailable(
                    remote = remote,
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode
                )
            } catch (e: Exception) {
                emitLog("Misslyckades att ladda ner uppdatering", e)
                _state.value = AppUpdateState.Error(
                    message = e.localizedMessage ?: "Kunde inte ladda ner uppdateringen. Försök igen senare.",
                    remote = remote,
                    isSilent = false
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    fun dismissForLater(versionCode: Int? = null) {
        downloadJob?.cancel()
        downloadJob = null
        if (versionCode != null && versionCode > 0) {
            snoozeStore.saveDismissed(versionCode, nowProvider())
        }
        _showDialog.value = false
        _state.value = AppUpdateState.Idle
    }

    fun dismissDialogOnly() {
        if (_state.value !is AppUpdateState.Downloading) {
            _showDialog.value = false
            if (_state.value is AppUpdateState.UpToDate || _state.value is AppUpdateState.Error) {
                _state.value = AppUpdateState.Idle
            }
        }
    }

    fun onUnknownSourcesSettingsResult() {
        val current = _state.value
        if (current is AppUpdateState.PermissionRequired && current.apkFile.exists()) {
            if (canInstallProvider()) {
                _state.value = AppUpdateState.ReadyToInstall(current.remote, current.apkFile)
                launchApkInstaller(current.apkFile)
            }
        }
    }

    fun launchApkInstaller(apkFile: File): Boolean {
        return try {
            val started = installerLauncher(apkFile)
            if (!started) {
                val remote = (_state.value as? AppUpdateState.ReadyToInstall)?.remote
                    ?: (_state.value as? AppUpdateState.PermissionRequired)?.remote
                _state.value = AppUpdateState.Error(
                    message = "Kunde inte öppna Androids installationsruta på denna enhet.",
                    remote = remote,
                    isSilent = false
                )
            }
            started
        } catch (e: Exception) {
            emitLog("Kunde inte öppna Androids paketinstallerare", e)
            val remote = (_state.value as? AppUpdateState.ReadyToInstall)?.remote
                ?: (_state.value as? AppUpdateState.PermissionRequired)?.remote
            _state.value = AppUpdateState.Error(
                message = "Kunde inte öppna installationsrutan på denna enhet (${e.localizedMessage ?: "okänt fel"}).",
                remote = remote,
                isSilent = false
            )
            false
        }
    }

    fun buildInstallIntent(context: Context, apkFile: File): Intent {
        return installIntentFactory(apkFile) ?: createInstallIntent(context, apkFile)
    }

    fun buildUnknownSourcesSettingsIntent(context: Context): Intent {
        return unknownSourcesIntentFactory() ?: createUnknownSourcesSettingsIntent(context)
    }

    private fun isSnoozed(remoteVersionCode: Int): Boolean {
        val dismissed = snoozeStore.getDismissed() ?: return false
        val (dismissedCode, dismissedAt) = dismissed
        if (dismissedCode != remoteVersionCode) return false
        return (nowProvider() - dismissedAt) < AppUpdateConfig.SNOOZE_DURATION_MS
    }

    private fun fetchLatestRemoteVersion(): RemoteAppVersion? {
        for (url in versionUrls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        emitLog("Versionskoll $url svarade HTTP ${response.code}", null)
                        return@use
                    }
                    val body = response.body?.string().orEmpty()
                    val parsed = AppUpdateVersionComparator.parseVersionJson(body)
                    if (parsed != null) {
                        return parsed
                    }
                }
            } catch (e: Exception) {
                emitLog("Misslyckades läsa version från $url: ${e.message}", null)
            }
        }
        return null
    }

    private fun downloadAndVerifyApk(remote: RemoteAppVersion): File {
        if (!AppUpdateVersionComparator.isAllowedUpdateHost(remote.apkUrl)) {
            throw IllegalArgumentException("Ogiltig nedladdningsadress för APK.")
        }

        val updatesDir = updatesDirProvider()
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        } else {
            updatesDir.listFiles()?.forEach { oldFile ->
                if (oldFile.isFile) oldFile.delete()
            }
        }

        val tempFile = File(updatesDir, "${AppUpdateConfig.UPDATE_APK_FILENAME}.part")
        val finalFile = File(updatesDir, AppUpdateConfig.UPDATE_APK_FILENAME)

        val request = Request.Builder()
            .url(remote.apkUrl)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Servern svarade med HTTP ${response.code} vid hämtning av APK.")
            }
            val finalUrl = response.request.url.toString()
            if (!AppUpdateVersionComparator.isAllowedUpdateHost(finalUrl)) {
                throw IllegalStateException("Omdirigering till ej betrodd adress blockerades.")
            }

            val body = response.body ?: throw IllegalStateException("Tomt svar vid nedladdning av APK.")
            val totalBytes = body.contentLength()
            var downloaded = 0L
            var lastEmitAt = 0L
            val headerBytes = ByteArray(4)
            var headerFilled = 0

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        if (headerFilled < 4) {
                            val needed = minOf(4 - headerFilled, read)
                            System.arraycopy(buffer, 0, headerBytes, headerFilled, needed)
                            headerFilled += needed
                        }
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastEmitAt >= 90L || (totalBytes > 0 && downloaded >= totalBytes)) {
                            lastEmitAt = now
                            val progress = if (totalBytes > 0) {
                                (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                null
                            }
                            _state.value = AppUpdateState.Downloading(
                                remote = remote,
                                progress = progress,
                                bytesDownloaded = downloaded,
                                totalBytes = totalBytes
                            )
                        }
                    }
                    output.flush()
                }
            }

            if (headerFilled < 4 || !AppUpdateVersionComparator.hasValidApkMagicHeader(headerBytes)) {
                tempFile.delete()
                throw IllegalStateException("Den nedladdade filen är inte en giltig APK.")
            }

            if (downloaded < minValidApkBytes) {
                tempFile.delete()
                throw IllegalStateException("Den nedladdade filen verkar ofullständig ($downloaded byte).")
            }
        }

        if (finalFile.exists()) finalFile.delete()
        if (!tempFile.renameTo(finalFile)) {
            tempFile.copyTo(finalFile, overwrite = true)
            tempFile.delete()
        }

        archiveVerifier(finalFile)
        return finalFile
    }

    private class SharedPrefsSnoozeStore(private val appContext: Context) : UpdateSnoozeStore {
        override fun getDismissed(): Pair<Int, Long>? {
            val prefs = appContext.getSharedPreferences(AppUpdateConfig.PREFS_NAME, Context.MODE_PRIVATE)
            val code = prefs.getInt(AppUpdateConfig.KEY_DISMISSED_VERSION_CODE, -1)
            val at = prefs.getLong(AppUpdateConfig.KEY_DISMISSED_AT_MS, 0L)
            return if (code > 0) code to at else null
        }

        override fun saveDismissed(versionCode: Int, timestampMs: Long) {
            val prefs = appContext.getSharedPreferences(AppUpdateConfig.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit {
                putInt(AppUpdateConfig.KEY_DISMISSED_VERSION_CODE, versionCode)
                putLong(AppUpdateConfig.KEY_DISMISSED_AT_MS, timestampMs)
            }
        }
    }

    companion object {
        private const val TAG = "AppUpdateManager"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        internal fun createInstallIntent(context: Context, apkFile: File): Intent {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            return Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        internal fun createUnknownSourcesSettingsIntent(context: Context): Intent {
            return Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        }

        private fun launchAndroidInstaller(context: Context, apkFile: File): Boolean {
            val intent = createInstallIntent(context, apkFile)
            context.startActivity(intent)
            return true
        }

        private fun verifyApkPackageArchive(context: Context, apkFile: File) {
            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }

            if (archiveInfo != null && archiveInfo.packageName != context.packageName) {
                apkFile.delete()
                throw IllegalStateException(
                    "APK-filen tillhör fel paketnamn (${archiveInfo.packageName})."
                )
            }
        }
    }
}
