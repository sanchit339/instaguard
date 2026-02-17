package com.instaguard

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.instaguard.data.BudgetRepository
import com.instaguard.ui.theme.InstaGuardTheme
import com.instaguard.update.ApkUpdateInstaller
import com.instaguard.update.GitHubUpdateChecker
import com.instaguard.update.UpdateInfo
import com.instaguard.update.UpdateNotifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            InstaGuardTheme {
                AppHome()
            }
        }
    }
}

@Composable
private fun AppHome() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { BudgetRepository(context.applicationContext) }
    val appVersion = remember { getAppVersionName(context) }

    var balanceMs by remember { mutableLongStateOf(0L) }
    var allowanceMs by remember { mutableLongStateOf(0L) }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var usageAccessEnabled by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(UpdateNotifier.canPostNotifications(context)) }

    var isCheckingUpdates by remember { mutableStateOf(true) }
    var isInstallingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }

    val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        notificationsEnabled = UpdateNotifier.canPostNotifications(context)
    }

    LaunchedEffect(Unit) {
        while (true) {
            val snapshot = repository.refresh()
            balanceMs = snapshot.balanceMs
            allowanceMs = snapshot.hourAllowanceMs
            accessibilityEnabled = isAccessibilityEnabled(context)
            usageAccessEnabled = hasUsageAccess(context)
            notificationsEnabled = UpdateNotifier.canPostNotifications(context)
            delay(1000L)
        }
    }

    LaunchedEffect(Unit) {
        isCheckingUpdates = true
        val result = GitHubUpdateChecker.check(appVersion)
        isCheckingUpdates = false
        result.onSuccess {
            updateInfo = it
            if (it.isUpdateAvailable && notificationsEnabled && it.releaseUrl.isNotBlank()) {
                UpdateNotifier.notifyUpdateAvailable(context, it.latestTag, it.releaseUrl)
            }
        }.onFailure {
            updateError = it.message ?: "Failed to check latest release"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "InstaGuard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Current hour quota", style = MaterialTheme.typography.titleMedium)
                Text(
                    formatBudget(allowanceMs),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    "Remaining: ${formatBudget(balanceMs)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Rule: 5 min/hour + max 1 min carry. No refill from 2:00 AM to 8:00 AM.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Permissions", style = MaterialTheme.typography.titleMedium)

                PermissionItem(
                    title = "Accessibility Service",
                    isEnabled = accessibilityEnabled,
                    actionText = "Open Accessibility",
                    description = "Needed for real-time app blocking.",
                    onAction = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                )

                PermissionItem(
                    title = "Usage Access",
                    isEnabled = usageAccessEnabled,
                    actionText = "Open Usage Access",
                    description = "Used for minute-level usage reconciliation.",
                    onAction = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                )

                PermissionItem(
                    title = "Update Notifications",
                    isEnabled = notificationsEnabled,
                    actionText = "Enable Notifications",
                    description = "Get notified when a new release is available.",
                    actionEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsEnabled,
                    onAction = {
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App Updates", style = MaterialTheme.typography.titleMedium)
                when {
                    isCheckingUpdates -> CircularProgressIndicator()
                    updateError != null -> Text(updateError!!, color = MaterialTheme.colorScheme.tertiary)
                    updateInfo?.isUpdateAvailable == true -> {
                        Text("New version ${updateInfo?.latestTag} is available.")
                        if (isInstallingUpdate) {
                            CircularProgressIndicator()
                        }
                        Button(onClick = {
                            val apkUrl = updateInfo?.apkDownloadUrl.orEmpty()
                            val tag = updateInfo?.latestTag.orEmpty()
                            if (apkUrl.isBlank() || tag.isBlank()) {
                                updateError = "Release APK asset not found."
                                return@Button
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                !context.packageManager.canRequestPackageInstalls()
                            ) {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                                updateError = "Allow install unknown apps for InstaGuard, then tap Update Now again."
                                return@Button
                            }

                            isInstallingUpdate = true
                            updateError = null
                            scope.launch {
                                ApkUpdateInstaller.downloadAndInstall(context, apkUrl, tag)
                                    .onFailure { error ->
                                        updateError = error.message ?: "Failed to download/install update."
                                    }
                                isInstallingUpdate = false
                            }
                        }) {
                            Text("Update In App")
                        }
                    }
                    else -> Text("You are on the latest version ($appVersion).")
                }
                Text(
                    "We notify you when a new release is available so you can keep the app bug free and secure.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "version $appVersion",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
    }
}

@Composable
private fun PermissionItem(
    title: String,
    isEnabled: Boolean,
    actionText: String,
    description: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "$title: ${if (isEnabled) "Enabled" else "Disabled"}",
            color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(text = description, style = MaterialTheme.typography.bodySmall)
        Button(onClick = onAction, enabled = actionEnabled) {
            Text(actionText)
        }
    }
}

private fun formatBudget(balanceMs: Long): String {
    val totalSeconds = balanceMs / 1000
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return String.format("%02dm %02ds", mins, secs)
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val expectedService = "${context.packageName}/${com.instaguard.service.InstaGuardAccessibilityService::class.java.name}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(expectedService, ignoreCase = true)
}

private fun hasUsageAccess(context: Context): Boolean {
    val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOpsManager.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun getAppVersionName(context: Context): String {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.versionName ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }
}
