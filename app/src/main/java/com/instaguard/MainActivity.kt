package com.instaguard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.instaguard.data.BudgetRepository
import com.instaguard.ui.theme.InstaGuardTheme
import kotlinx.coroutines.delay

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
    val repository = remember { BudgetRepository(context.applicationContext) }

    var balanceMs by remember { mutableLongStateOf(0L) }
    var accessibilityEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val snapshot = repository.tick(isConsuming = false)
            balanceMs = snapshot.balanceMs
            accessibilityEnabled = isAccessibilityEnabled(context)
            delay(1000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("InstaGuard", style = MaterialTheme.typography.headlineMedium)

        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Remaining Instagram budget", style = MaterialTheme.typography.titleMedium)
                Text(
                    formatBudget(balanceMs),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "You earn 5 minutes every hour. Unused time carries forward.",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Setup", style = MaterialTheme.typography.titleMedium)
                Text(if (accessibilityEnabled) "Accessibility: Enabled" else "Accessibility: Disabled")
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    },
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text("Open Accessibility Settings")
                }
            }
        }

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text("Strict mode details", style = MaterialTheme.typography.titleMedium)
                Text("1. If Instagram budget is exhausted, app sends Instagram to Home.")
                Text("2. Block screen keeps reopening while budget is zero.")
                Text("3. To bypass permanently, uninstall InstaGuard.")
            }
        }
    }
}

private fun formatBudget(balanceMs: Long): String {
    val totalSeconds = balanceMs / 1000
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return String.format("%02dm %02ds", mins, secs)
}

private fun isAccessibilityEnabled(context: android.content.Context): Boolean {
    val expectedService = "${context.packageName}/${com.instaguard.service.InstaGuardAccessibilityService::class.java.name}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(expectedService, ignoreCase = true)
}
