package io.netscope.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.netscope.NetScope
import io.netscope.notification.NetScopeNotificationManager
import io.netscope.ui.NetScopeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : ComponentActivity() {

    // Initialised eagerly in onCreate — NOT lazy — so that NetScope.repository
    // is ready before NetScopeNotificationManager.init() is called.
    private lateinit var client: OkHttpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Build the interceptor first — this initialises NetScope.repository.
        client = OkHttpClient.Builder()
            .addInterceptor(
                NetScope.interceptor(this) {
                    maxTransactions(200)
                    redactHeaders("X-Api-Key")
                }
            )
            .build()

        // 2. Now NetScope.repository is ready — safe to start the notification.
        NetScopeNotificationManager.init(this, launchActivity = NetScopeActivity::class.java)
        setContent {
            MaterialTheme {
                SampleScreen(client = client)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleScreen(client: OkHttpClient) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastResult by remember { mutableStateOf("Tap a button to fire a request, then open NetScope to inspect it.") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("NetScope Sample") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { NetScopeActivity.launch(context) },
                icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                text = { Text("Open NetScope") },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Status card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Text(
                    text = lastResult,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }

            SectionLabel("2xx — Success")

            RequestButton("GET  /posts  (list)") {
                scope.launch {
                    lastResult = fireRequest(client, Request.Builder()
                        .url("https://jsonplaceholder.typicode.com/posts")
                        .build())
                }
            }

            RequestButton("GET  /posts/1  (single)") {
                scope.launch {
                    lastResult = fireRequest(client, Request.Builder()
                        .url("https://jsonplaceholder.typicode.com/posts/1")
                        .build())
                }
            }

            RequestButton("POST  /posts  (create)") {
                scope.launch {
                    val body = """{"title":"NetScope Test","body":"Hello from sample app","userId":1}"""
                        .toRequestBody("application/json".toMediaType())
                    lastResult = fireRequest(client, Request.Builder()
                        .url("https://jsonplaceholder.typicode.com/posts")
                        .post(body)
                        .build())
                }
            }

            RequestButton("PUT  /posts/1  (update)") {
                scope.launch {
                    val body = """{"id":1,"title":"Updated Title","body":"Updated body","userId":1}"""
                        .toRequestBody("application/json".toMediaType())
                    lastResult = fireRequest(client, Request.Builder()
                        .url("https://jsonplaceholder.typicode.com/posts/1")
                        .put(body)
                        .build())
                }
            }

            RequestButton("DELETE  /posts/1") {
                scope.launch {
                    lastResult = fireRequest(client, Request.Builder()
                        .url("https://jsonplaceholder.typicode.com/posts/1")
                        .delete()
                        .build())
                }
            }

            HorizontalDivider()
            SectionLabel("4xx / 5xx — Errors")

            RequestButton("GET  /posts/9999  (404)", isError = true) {
                scope.launch {
                    lastResult = fireRequest(client, Request.Builder()
                        .url("https://jsonplaceholder.typicode.com/posts/9999")
                        .build())
                }
            }

            RequestButton("GET  /status/500  (server error)", isError = true) {
                scope.launch {
                    lastResult = fireRequest(client, Request.Builder()
                        .url("https://httpbin.org/status/500")
                        .build())
                }
            }

            RequestButton("GET  /status/401  (unauthorized)", isError = true) {
                scope.launch {
                    lastResult = fireRequest(client, Request.Builder()
                        .url("https://httpbin.org/status/401")
                        .addHeader("Authorization", "Bearer secret-token")
                        .build())
                }
            }

            HorizontalDivider()
            SectionLabel("Network failure")

            RequestButton("GET  invalid-host  (connection error)", isError = true) {
                scope.launch {
                    lastResult = fireRequest(client, Request.Builder()
                        .url("https://this.host.does.not.exist.netscope/test")
                        .build())
                }
            }

            RequestButton("Fire all requests at once") {
                scope.launch {
                    val urls = listOf(
                        "https://jsonplaceholder.typicode.com/posts",
                        "https://jsonplaceholder.typicode.com/users",
                        "https://jsonplaceholder.typicode.com/todos",
                        "https://jsonplaceholder.typicode.com/albums",
                    )
                    lastResult = "Firing ${urls.size} requests…"
                    urls.forEach { url ->
                        launch {
                            fireRequest(client, Request.Builder().url(url).build())
                        }
                    }
                    lastResult = "Fired ${urls.size} requests — open NetScope to inspect"
                }
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun RequestButton(label: String, isError: Boolean = false, onClick: () -> Unit) {
    if (isError) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(label, fontFamily = FontFamily.Monospace)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label, fontFamily = FontFamily.Monospace)
        }
    }
}

private suspend fun fireRequest(client: OkHttpClient, request: Request): String =
    withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(request).execute()
            val bodySnippet = response.body?.string()?.take(120) ?: ""
            response.close()
            "${request.method} ${request.url.encodedPath} → ${response.code}\n$bodySnippet"
        } catch (e: Exception) {
            "${request.method} ${request.url.host} → ERROR: ${e.message}"
        }
    }
