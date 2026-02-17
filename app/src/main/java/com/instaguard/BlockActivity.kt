package com.instaguard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.instaguard.data.BudgetRepository
import com.instaguard.ui.theme.InstaGuardTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BlockActivity : ComponentActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var repository: BudgetRepository
    private val handler = Handler(Looper.getMainLooper())

    private val pollRunnable = object : Runnable {
        override fun run() {
            scope.launch {
                val snapshot = repository.refresh()
                if (snapshot.balanceMs > 0L) {
                    finish()
                }
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = BudgetRepository(applicationContext)

        setContent {
            InstaGuardTheme {
                BackHandler(enabled = true) { }

                var balanceMs by remember { mutableLongStateOf(0L) }

                LaunchedEffect(Unit) {
                    while (true) {
                        balanceMs = repository.current().balanceMs
                        delay(1000L)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Instagram locked",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "This hour's quota is exhausted.",
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Current remaining budget: ${balanceMs / 1000}s",
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Next hour gets 5 minutes plus max 1 minute carry from previous hour.",
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Button(
                        onClick = {
                            startActivity(
                                Intent(Intent.ACTION_DELETE).apply {
                                    data = Uri.parse("package:$packageName")
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        Text("Uninstall InstaGuard")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(pollRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        scope.cancel()
        super.onDestroy()
    }
}
