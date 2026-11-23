package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.update.AppUpdateManager
import com.example.update.AppUpdateState
import java.util.Locale

@Composable
fun AppUpdateDialog(
    appUpdateManager: AppUpdateManager
) {
    val state by appUpdateManager.state.collectAsState()
    val showDialog by appUpdateManager.showDialog.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        appUpdateManager.onUnknownSourcesSettingsResult()
    }

    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && state is AppUpdateState.PermissionRequired) {
                appUpdateManager.onUnknownSourcesSettingsResult()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!showDialog || state is AppUpdateState.Idle) return

    AppUpdateDialogContent(
        state = state,
        onStartDownload = { remote -> appUpdateManager.startDownload(remote) },
        onDismissLater = { versionCode -> appUpdateManager.dismissForLater(versionCode) },
        onCancelDownload = { appUpdateManager.cancelDownload() },
        onOpenUnknownSourcesSettings = {
            unknownSourcesLauncher.launch(appUpdateManager.buildUnknownSourcesSettingsIntent(context))
        },
        onLaunchInstaller = { apkFile -> appUpdateManager.launchApkInstaller(apkFile) },
        onRetryCheck = { appUpdateManager.checkForUpdates(manual = true) },
        onCloseDialog = { appUpdateManager.dismissDialogOnly() }
    )
}

@Composable
fun AppUpdateDialogContent(
    state: AppUpdateState,
    onStartDownload: (com.example.update.RemoteAppVersion) -> Unit,
    onDismissLater: (Int?) -> Unit,
    onCancelDownload: () -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onLaunchInstaller: (java.io.File) -> Unit,
    onRetryCheck: () -> Unit,
    onCloseDialog: () -> Unit
) {
    when (state) {
        AppUpdateState.Idle -> Unit

        AppUpdateState.Checking -> {
            AlertDialog(
                onDismissRequest = onCloseDialog,
                icon = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = EmeraldPrimary,
                        strokeWidth = 3.dp
                    )
                },
                title = {
                    Text(
                        text = "Söker efter uppdatering...",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Kontrollerar om det finns en ny version av Zevra på GitHub.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(onClick = onCloseDialog) {
                        Text("Dölj")
                    }
                }
            )
        }

        is AppUpdateState.UpToDate -> {
            AlertDialog(
                onDismissRequest = onCloseDialog,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Uppdaterad",
                            tint = EmeraldDark,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = "Zevra är uppdaterad",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Du kör redan den senaste versionen (v${state.currentVersionName}, byggkod ${state.currentVersionCode}).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onCloseDialog,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("update_uptodate_ok_button")
                    ) {
                        Text("Toppen", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        is AppUpdateState.UpdateAvailable -> {
            AlertDialog(
                onDismissRequest = { onDismissLater(state.remote.versionCode) },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Ny uppdatering",
                            tint = EmeraldDark,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = "Ny version av Zevra finns!",
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EmeraldPrimary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Installerad: v${state.currentVersionName}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Ny: v${state.remote.versionName}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmeraldDark
                                )
                            }
                        }

                        if (state.remote.releaseNotes.isNotBlank()) {
                            Text(
                                text = state.remote.releaseNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Text(
                                text = "En ny version är klar att hämtas och installeras direkt i appen.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Du behöver inte öppna webbläsaren och all din loggade data och Google-inloggning sparas.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onStartDownload(state.remote) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("update_now_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Uppdatera nu", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { onDismissLater(state.remote.versionCode) },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("update_later_button")
                    ) {
                        Text("Senare")
                    }
                }
            )
        }

        is AppUpdateState.Downloading -> {
            val percent = state.progress?.let { (it * 100).toInt().coerceIn(0, 100) }
            val downloadedMb = formatMb(state.bytesDownloaded)
            val totalMb = if (state.totalBytes > 0) formatMb(state.totalBytes) else null

            AlertDialog(
                onDismissRequest = {},
                icon = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Laddar ner uppdatering",
                            tint = EmeraldDark,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = "Laddar ner v${state.remote.versionName}...",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.progress != null) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                color = EmeraldPrimary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .testTag("update_download_progress")
                            )
                        } else {
                            LinearProgressIndicator(
                                color = EmeraldPrimary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .testTag("update_download_progress")
                            )
                        }

                        val progressLabel = buildString {
                            if (percent != null) append("$percent% • ")
                            append(downloadedMb)
                            if (totalMb != null) append(" / $totalMb")
                        }
                        Text(
                            text = progressLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    OutlinedButton(
                        onClick = onCancelDownload,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("update_cancel_button")
                    ) {
                        Text("Avbryt")
                    }
                }
            )
        }

        is AppUpdateState.PermissionRequired -> {
            AlertDialog(
                onDismissRequest = onCloseDialog,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Behörighet krävs",
                            tint = EmeraldDark,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = "Tillåt installation från Zevra",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Uppdateringen (v${state.remote.versionName}) är nedladdad! Slå på \"Tillåt från denna källa\" för Zevra en gång i inställningarna så startar installationen automatiskt direkt efteråt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onOpenUnknownSourcesSettings,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("update_permission_settings_button")
                    ) {
                        Text("Öppna inställningar", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onCloseDialog,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Avbryt")
                    }
                }
            )
        }

        is AppUpdateState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = onCloseDialog,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.InstallMobile,
                            contentDescription = "Redo att installera",
                            tint = EmeraldDark,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = "Installera Zevra v${state.remote.versionName}",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Nedladdningen är klar. Om Androids installationsruta inte öppnades automatiskt, tryck på Installera nedan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onLaunchInstaller(state.apkFile) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("update_install_button")
                    ) {
                        Text("Installera", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onCloseDialog,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Stäng")
                    }
                }
            )
        }

        is AppUpdateState.Error -> {
            AlertDialog(
                onDismissRequest = onCloseDialog,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Uppdateringsfel",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = "Uppdatering misslyckades",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (state.remote != null) {
                                onStartDownload(state.remote)
                            } else {
                                onRetryCheck()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("update_retry_button")
                    ) {
                        Text("Försök igen", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onCloseDialog,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Stäng")
                    }
                }
            )
        }
    }
}

private fun formatMb(bytes: Long): String {
    val mb = bytes.coerceAtLeast(0L) / (1024.0 * 1024.0)
    return String.format(Locale.forLanguageTag("sv-SE"), "%.1f MB", mb)
}
