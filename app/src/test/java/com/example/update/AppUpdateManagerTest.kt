package com.example.update

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class InMemorySnoozeStore : UpdateSnoozeStore {
        private var saved: Pair<Int, Long>? = null
        override fun getDismissed(): Pair<Int, Long>? = saved
        override fun saveDismissed(versionCode: Int, timestampMs: Long) {
            saved = versionCode to timestampMs
        }
    }

    private fun fakeClient(
        versionJson: String,
        apkPayload: ByteArray = ByteArray(1024).apply {
            this[0] = 'P'.code.toByte()
            this[1] = 'K'.code.toByte()
            this[2] = 0x03.toByte()
            this[3] = 0x04.toByte()
        },
        failVersionJson: Boolean = false
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                when {
                    url.endsWith("version.json") && !failVersionJson -> {
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(versionJson.toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    url.endsWith(".apk") -> {
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(apkPayload.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                            .build()
                    }
                    else -> {
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .body("".toResponseBody("text/plain".toMediaType()))
                            .build()
                    }
                }
            }
            .build()
    }

    @Test
    fun `version json parser validates trusted hosts and rejects untrusted urls`() {
        val validJson = """
            {
              "versionCode": 120,
              "versionName": "1.1.20",
              "apkUrl": "https://github.com/Zumdewisk/NYNYNY/releases/download/latest/Zevra.apk",
              "releaseNotes": "Snabbare röstloggning",
              "publishedAt": "2026-11-23T22:00:00Z"
            }
        """.trimIndent()

        val parsed = AppUpdateVersionComparator.parseVersionJson(validJson)
        assertNotNull(parsed)
        assertEquals(120, parsed!!.versionCode)
        assertEquals("1.1.20", parsed.versionName)
        assertEquals("Snabbare röstloggning", parsed.releaseNotes)

        val pagesJson = """
            {
              "versionCode": 121,
              "versionName": "1.1.21",
              "apkUrl": "https://zumdewisk.github.io/NYNYNY/Zevra.apk"
            }
        """.trimIndent()
        assertNotNull(AppUpdateVersionComparator.parseVersionJson(pagesJson))

        val untrustedJson = """
            {
              "versionCode": 120,
              "versionName": "1.1.20",
              "apkUrl": "https://evil.example.com/Zevra.apk"
            }
        """.trimIndent()
        assertNull(AppUpdateVersionComparator.parseVersionJson(untrustedJson))

        val httpJson = """
            {
              "versionCode": 120,
              "versionName": "1.1.20",
              "apkUrl": "http://github.com/Zumdewisk/NYNYNY/releases/download/latest/Zevra.apk"
            }
        """.trimIndent()
        assertNull(AppUpdateVersionComparator.parseVersionJson(httpJson))
    }

    @Test
    fun `repo root version json file parses cleanly`() {
        val candidates = listOf(File("version.json"), File("../version.json"))
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull("version.json saknas i repo-roten", file)
        val parsed = AppUpdateVersionComparator.parseVersionJson(file!!.readText())
        assertNotNull("version.json kunde inte tolkas", parsed)
        assertTrue(parsed!!.versionCode >= 2)
        assertTrue(AppUpdateVersionComparator.isAllowedUpdateHost(parsed.apkUrl))
    }

    @Test
    fun `version comparator detects newer versionCode and semantic versionName`() {
        val higherCode = RemoteAppVersion(
            versionCode = 5,
            versionName = "1.1",
            apkUrl = "https://github.com/Zumdewisk/NYNYNY/releases/download/latest/Zevra.apk"
        )
        assertTrue(AppUpdateVersionComparator.isNewerVersion(higherCode, currentVersionCode = 2, currentVersionName = "1.1"))

        val sameCodeHigherSemver = RemoteAppVersion(
            versionCode = 2,
            versionName = "1.2.1",
            apkUrl = "https://github.com/Zumdewisk/NYNYNY/releases/download/latest/Zevra.apk"
        )
        assertTrue(AppUpdateVersionComparator.isNewerVersion(sameCodeHigherSemver, currentVersionCode = 2, currentVersionName = "1.1"))

        val equalVersion = RemoteAppVersion(
            versionCode = 2,
            versionName = "1.1",
            apkUrl = "https://github.com/Zumdewisk/NYNYNY/releases/download/latest/Zevra.apk"
        )
        assertFalse(AppUpdateVersionComparator.isNewerVersion(equalVersion, currentVersionCode = 2, currentVersionName = "1.1"))
    }

    @Test
    fun `checkForUpdates detects available update and snoozing respects automatic vs manual checks`() {
        val snoozeStore = InMemorySnoozeStore()
        val dispatcher = UnconfinedTestDispatcher()
        val updatesDir = tempFolder.newFolder("updates1")

        val remoteJson = """
            {
              "versionCode": 105,
              "versionName": "1.1.5",
              "apkUrl": "https://github.com/Zumdewisk/NYNYNY/releases/download/latest/Zevra.apk",
              "releaseNotes": "Ny version redo"
            }
        """.trimIndent()

        var now = 1_000_000L
        val manager1 = AppUpdateManager(
            httpClient = fakeClient(remoteJson),
            versionUrls = AppUpdateConfig.VERSION_CHECK_URLS,
            currentVersionCode = 2,
            currentVersionName = "1.1",
            minValidApkBytes = 512L,
            nowProvider = { now },
            canInstallProvider = { false },
            snoozeStore = snoozeStore,
            updatesDirProvider = { updatesDir },
            archiveVerifier = {},
            installerLauncher = { true },
            installIntentFactory = { null },
            unknownSourcesIntentFactory = { null },
            mainDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            emitLog = { _, _ -> }
        )

        manager1.checkForUpdates(manual = false)
        assertTrue(manager1.state.value is AppUpdateState.UpdateAvailable)
        assertTrue(manager1.showDialog.value)

        // User dismisses with "Senare"
        manager1.dismissForLater(105)
        assertFalse(manager1.showDialog.value)
        assertTrue(manager1.state.value is AppUpdateState.Idle)

        // Next automatic startup check within 24h stays silent
        now += 60_000L
        val manager2 = AppUpdateManager(
            httpClient = fakeClient(remoteJson),
            versionUrls = AppUpdateConfig.VERSION_CHECK_URLS,
            currentVersionCode = 2,
            currentVersionName = "1.1",
            minValidApkBytes = 512L,
            nowProvider = { now },
            canInstallProvider = { false },
            snoozeStore = snoozeStore,
            updatesDirProvider = { updatesDir },
            archiveVerifier = {},
            installerLauncher = { true },
            installIntentFactory = { null },
            unknownSourcesIntentFactory = { null },
            mainDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            emitLog = { _, _ -> }
        )
        manager2.checkForUpdates(manual = false)
        assertTrue(manager2.state.value is AppUpdateState.Idle)
        assertFalse(manager2.showDialog.value)

        // Manual check from ProfileScreen ignores snooze and opens prompt
        manager2.checkForUpdates(manual = true)
        assertTrue(manager2.state.value is AppUpdateState.UpdateAvailable)
        assertTrue(manager2.showDialog.value)
    }

    @Test
    fun `checkForUpdates handles up to date and network error cleanly for automatic vs manual`() {
        val snoozeStore = InMemorySnoozeStore()
        val dispatcher = UnconfinedTestDispatcher()
        val updatesDir = tempFolder.newFolder("updates2")
        val sameVersionJson = """
            {
              "versionCode": 2,
              "versionName": "1.1",
              "apkUrl": "https://github.com/Zumdewisk/NYNYNY/releases/download/latest/Zevra.apk"
            }
        """.trimIndent()

        val upToDateManager = AppUpdateManager(
            httpClient = fakeClient(sameVersionJson),
            versionUrls = AppUpdateConfig.VERSION_CHECK_URLS,
            currentVersionCode = 2,
            currentVersionName = "1.1",
            minValidApkBytes = 512L,
            nowProvider = { 1_000L },
            canInstallProvider = { true },
            snoozeStore = snoozeStore,
            updatesDirProvider = { updatesDir },
            archiveVerifier = {},
            installerLauncher = { true },
            installIntentFactory = { null },
            unknownSourcesIntentFactory = { null },
            mainDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            emitLog = { _, _ -> }
        )
        upToDateManager.checkForUpdates(manual = true)
        assertTrue(upToDateManager.state.value is AppUpdateState.UpToDate)
        assertTrue(upToDateManager.showDialog.value)

        val offlineManager = AppUpdateManager(
            httpClient = fakeClient("{}", failVersionJson = true),
            versionUrls = AppUpdateConfig.VERSION_CHECK_URLS,
            currentVersionCode = 2,
            currentVersionName = "1.1",
            minValidApkBytes = 512L,
            nowProvider = { 1_000L },
            canInstallProvider = { true },
            snoozeStore = snoozeStore,
            updatesDirProvider = { updatesDir },
            archiveVerifier = {},
            installerLauncher = { true },
            installIntentFactory = { null },
            unknownSourcesIntentFactory = { null },
            mainDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            emitLog = { _, _ -> }
        )
        offlineManager.checkForUpdates(manual = false)
        assertTrue(offlineManager.state.value is AppUpdateState.Idle)
        assertFalse(offlineManager.showDialog.value)

        offlineManager.checkForUpdates(manual = true)
        assertTrue(offlineManager.state.value is AppUpdateState.Error)
        assertTrue(offlineManager.showDialog.value)
    }

    @Test
    fun `startDownload verifies APK payload, prompts unknown sources permission, then launches installer when allowed`() {
        val snoozeStore = InMemorySnoozeStore()
        val dispatcher = UnconfinedTestDispatcher()
        val updatesDir = tempFolder.newFolder("updates3")
        var allowUnknownSources = false
        var launchedFile: File? = null

        val remote = RemoteAppVersion(
            versionCode = 110,
            versionName = "1.1.10",
            apkUrl = "https://github.com/Zumdewisk/NYNYNY/releases/download/latest/Zevra.apk",
            releaseNotes = "Buggfixar"
        )

        val manager = AppUpdateManager(
            httpClient = fakeClient("{}", apkPayload = ByteArray(2048).apply {
                this[0] = 'P'.code.toByte()
                this[1] = 'K'.code.toByte()
                this[2] = 0x03.toByte()
                this[3] = 0x04.toByte()
            }),
            versionUrls = AppUpdateConfig.VERSION_CHECK_URLS,
            currentVersionCode = 2,
            currentVersionName = "1.1",
            minValidApkBytes = 512L,
            nowProvider = { 1_000L },
            canInstallProvider = { allowUnknownSources },
            snoozeStore = snoozeStore,
            updatesDirProvider = { updatesDir },
            archiveVerifier = {},
            installerLauncher = { file ->
                launchedFile = file
                true
            },
            installIntentFactory = { null },
            unknownSourcesIntentFactory = { null },
            mainDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            emitLog = { _, _ -> }
        )

        manager.startDownload(remote)

        val permState = manager.state.value as? AppUpdateState.PermissionRequired
        assertNotNull("Expected PermissionRequired state when unknown sources not granted yet", permState)
        assertTrue(permState!!.apkFile.exists())
        assertEquals(2048L, permState.apkFile.length())
        assertNull(launchedFile)

        // User grants "Installera okända appar" and returns from Android settings
        allowUnknownSources = true
        manager.onUnknownSourcesSettingsResult()

        assertTrue(manager.state.value is AppUpdateState.ReadyToInstall)
        assertEquals(permState.apkFile, launchedFile)
    }

    @Test
    fun `startDownload rejects non-APK payload such as HTML error page`() {
        val snoozeStore = InMemorySnoozeStore()
        val dispatcher = UnconfinedTestDispatcher()
        val updatesDir = tempFolder.newFolder("updates4")

        val remote = RemoteAppVersion(
            versionCode = 110,
            versionName = "1.1.10",
            apkUrl = "https://github.com/Zumdewisk/NYNYNY/releases/download/latest/Zevra.apk"
        )

        val manager = AppUpdateManager(
            httpClient = fakeClient("{}", apkPayload = "<html>404 Not Found</html>".toByteArray()),
            versionUrls = AppUpdateConfig.VERSION_CHECK_URLS,
            currentVersionCode = 2,
            currentVersionName = "1.1",
            minValidApkBytes = 10L,
            nowProvider = { 1_000L },
            canInstallProvider = { true },
            snoozeStore = snoozeStore,
            updatesDirProvider = { updatesDir },
            archiveVerifier = {},
            installerLauncher = { true },
            installIntentFactory = { null },
            unknownSourcesIntentFactory = { null },
            mainDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            emitLog = { _, _ -> }
        )

        manager.startDownload(remote)

        val errorState = manager.state.value as? AppUpdateState.Error
        assertNotNull(errorState)
        assertTrue(errorState!!.message.contains("giltig APK"))
        assertEquals(0, updatesDir.listFiles()?.count { it.name.endsWith(".apk") } ?: 0)
    }
}
