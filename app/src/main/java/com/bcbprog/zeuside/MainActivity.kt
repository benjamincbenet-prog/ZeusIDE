package com.bcbprog.zeuside

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.bcbprog.zeuside.network.CodeSandboxClient
import com.bcbprog.zeuside.ui.ZeppIdeComposeScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val sandboxClient = CodeSandboxClient("https://YOUR-SANDBOX-ID-3000.csb.app")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var buildLogs by remember { mutableStateOf("") }
                var qrUrl by remember { mutableStateOf<String?>(null) }
                var isBuilding by remember { mutableStateOf(false) }

                ZeppIdeComposeScreen(
                    buildLogs = buildLogs,
                    qrUrl = qrUrl,
                    isBuilding = isBuilding,
                    onBuildClick = { filesMap ->
                        lifecycleScope.launch {
                            isBuilding = true
                            buildLogs = "Starting build on remote CodeSandbox server...\n"
                            
                            val response = sandboxClient.triggerBuild(filesMap)
                            isBuilding = false
                            buildLogs = response.logs ?: response.error ?: "Unknown build response"
                        }
                    },
                    onPreviewClick = { filesMap ->
                        lifecycleScope.launch {
                            isBuilding = true
                            buildLogs = "Generating QR Preview link...\n"

                            val response = sandboxClient.triggerBuild(filesMap)
                            isBuilding = false
                            
                            if (response.success && !response.artifacts.isNullOrEmpty()) {
                                // Direct public URL to the generated .zab file on CodeSandbox
                                qrUrl = "https://YOUR-SANDBOX-ID-3000.csb.app/zepp_project/dist/${response.artifacts.first()}"
                                buildLogs += "\nQR Code generated successfully."
                            } else {
                                buildLogs += "\nFailed to generate preview package."
                            }
                        }
                    },
                    onDismissQr = { qrUrl = null }
                )
            }
        }
    }
}
