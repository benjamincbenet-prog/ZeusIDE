package com.bcbprog.zeuside.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

data class ProjectFile(
    val path: String,
    val language: String,
    var content: String
)

private class WebAppInterface(
    private val getActiveFilePath: () -> String,
    private val fileMap: SnapshotStateMap<String, ProjectFile>
) {
    @JavascriptInterface
    fun onCodeChanged(newContent: String) {
        fileMap[getActiveFilePath()]?.let { file -> file.content = newContent }
    }
}

@SuppressLint("JavascriptInterface")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeppIdeComposeScreen(
    buildLogs: String,
    qrUrl: String?,
    isBuilding: Boolean,
    onBuildClick: (Map<String, String>) -> Unit,
    onPreviewClick: (Map<String, String>) -> Unit,
    onDismissQr: () -> Unit
) {
    val fileMap = remember {
        mutableStateMapOf(
            "page/index.js" to ProjectFile("page/index.js", "javascript", "import { getDeviceInfo } from '@zeppos/device-api';\n\nPage({\n  onInit() {\n    const info = getDeviceInfo();\n    console.log('Screen Width:', info.width);\n  }\n});"),
            "app.js" to ProjectFile("app.js", "javascript", "App({\n  onCreate() {\n    console.log('App Created');\n  }\n});"),
            "app.json" to ProjectFile("app.json", "json", "{\n  \"configVersion\": \"v2\",\n  \"app\": { \"appName\": \"ZeppApp\" }\n}")
        )
    }

    val activeFilePathState = remember { mutableStateOf("page/index.js") }
    var activeFilePath by activeFilePathState
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isTerminalExpanded by remember { mutableStateOf(false) }

    val webAppInterface = remember { WebAppInterface(getActiveFilePath = { activeFilePathState.value }, fileMap = fileMap) }

    // Auto-expand terminal when new logs arrive
    LaunchedEffect(buildLogs) {
        if (buildLogs.isNotBlank()) isTerminalExpanded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚡ Zepp OS Local IDE", fontSize = 18.sp) },
                actions = {
                    IconButton(
                        onClick = { onPreviewClick(fileMap.mapValues { it.value.content }) },
                        enabled = !isBuilding
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = "Preview QR")
                    }
                    Button(
                        onClick = { onBuildClick(fileMap.mapValues { it.value.content }) },
                        enabled = !isBuilding,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (isBuilding) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isBuilding) "Building..." else "Build App")
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = Color(0xFF252526),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF181818))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Main Workspace Layout
                Row(modifier = Modifier.weight(1f)) {
                    // 1. File Explorer
                    Column(
                        modifier = Modifier
                            .width(200.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF252526))
                    ) {
                        Text("EXPLORER", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
                        LazyColumn {
                            items(fileMap.keys.toList()) { filePath ->
                                val isSelected = filePath == activeFilePath
                                Text(
                                    text = "📄 $filePath",
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isSelected) Color(0xFF37373D) else Color.Transparent)
                                        .clickable {
                                            activeFilePath = filePath
                                            val targetFile = fileMap[filePath]
                                            if (targetFile != null) {
                                                webViewRef?.evaluateJavascript(
                                                    "setEditorContent(`${targetFile.content.replace("`", "\\`")}`, '${targetFile.language}');",
                                                    null
                                                )
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Divider(color = Color(0xFF3C3C3C), modifier = Modifier.fillMaxHeight().width(1.dp))

                    // 2. Monaco Code Editor
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    // Enable debugging (View in Chrome via chrome://inspect)
                                    WebView.setWebContentsDebuggingEnabled(true)
                                    
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        allowFileAccess = true
                                        allowContentAccess = true
                                        // Allow loading CDN scripts over local file asset URLs
                                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    }
                                    
                                    addJavascriptInterface(webAppInterface, "AndroidBridge")
                                    
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            fileMap[activeFilePath]?.let { file ->
                                                // Safely pass initial code content into Monaco
                                                val safeContent = file.content
                                                    .replace("\\", "\\\\")
                                                    .replace("`", "\\`")
                                                    .replace("\$", "\\\$")
                                                
                                                evaluateJavascript(
                                                    "if (typeof setEditorContent === 'function') { setEditorContent(`$safeContent`, '${file.language}'); }",
                                                    null
                                                )
                                            }
                                        }
                                    }
                                    loadUrl("file:///android_asset/monaco_editor.html")
                                    webViewRef = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                    }
                }

                // 3. Collapsible Terminal Logs Drawer
                AnimatedVisibility(visible = isTerminalExpanded || buildLogs.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color(0xFF1E1E1E))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .background(Color(0xFF2D2D2D))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("TERMINAL OUTPUT", color = Color.LightGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            IconButton(onClick = { isTerminalExpanded = false }, modifier = Modifier.size(18.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = buildLogs.ifBlank { "Ready to build." },
                                color = if (buildLogs.contains("ERROR") || buildLogs.contains("Failed")) Color(0xFFFF6B6B) else Color(0xFF4EC9B0),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // 4. QR Code Installation Dialog
            if (qrUrl != null) {
                AlertDialog(
                    onDismissRequest = onDismissQr,
                    title = { Text("Scan with Zepp Mobile App", fontSize = 16.sp) },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Image(
                                painter = QrCodeUtils.generateQrPainter(qrUrl),
                                contentDescription = "Installation QR Code",
                                modifier = Modifier.size(240.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(qrUrl, fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = onDismissQr) { Text("Close") }
                    },
                    containerColor = Color(0xFF252526),
                    titleContentColor = Color.White,
                    textContentColor = Color.White
                )
            }
        }
    }
}
