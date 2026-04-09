package com.vkturn.proxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import android.os.PowerManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vkturn.proxy.viewmodel.MainViewModel
import com.vkturn.proxy.states.ProxyState
import com.vkturn.proxy.ui.theme.*
import com.vkturn.proxy.ui.components.CurrentProfileHeader
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val proxyState by viewModel.proxyState.collectAsStateWithLifecycle()
    val proxyLogs by viewModel.proxyLogs.collectAsStateWithLifecycle()
    val isKernelPresent by viewModel.isKernelPresent.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val selectedProfileId by viewModel.selectedProfileId.collectAsStateWithLifecycle()
    

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val pm = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    
    val kernelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                val result = viewModel.importKernel(it)
                if (result.isSuccess) {
                    Toast.makeText(context, "Ядро импортировано", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, result.exceptionOrNull()?.message ?: "Ошибка", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    var isIgnoringBattery by remember { 
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        ) 
    }

    LaunchedEffect(isIgnoringBattery) {
        if (!isIgnoringBattery) {
            while (true) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val current = pm.isIgnoringBatteryOptimizations(context.packageName)
                    if (current) {
                        isIgnoringBattery = true
                        break
                    }
                } else {
                    break
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }
    
    // Captcha Dialog handling
    if (proxyState is ProxyState.CaptchaRequired) {
        CaptchaWebViewDialog(
            captchaUrl = (proxyState as ProxyState.CaptchaRequired).url,
            onDismiss = { viewModel.dismissCaptcha() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(16.dp))

        // Center Button
        val buttonColor = when {
            !isKernelPresent -> StatusRed
            proxyState is ProxyState.Running -> StatusGreenDark
            proxyState is ProxyState.Starting || proxyState is ProxyState.CaptchaRequired -> StatusYellow
            else -> MaterialTheme.colorScheme.primary
        }

        Button(
            onClick = {
                if (proxyState is ProxyState.Running || proxyState is ProxyState.CaptchaRequired) {
                    viewModel.stopProxy()
                } else {
                    viewModel.startProxy()
                }
            },
            enabled = isKernelPresent,
            modifier = Modifier
                .size(150.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                disabledContainerColor = StatusRed,
                disabledContentColor = Color.White
            )
        ) {
            Text(
                text = when {
                    !isKernelPresent -> "НЕТ ЯДРА"
                    proxyState is ProxyState.Running -> "ОСТАНОВИТЬ"
                    proxyState is ProxyState.Starting -> "ЗАПУСК..."
                    proxyState is ProxyState.CaptchaRequired -> "КАПЧА"
                    else -> "ЗАПУСТИТЬ"
                },
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                softWrap = false,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!isIgnoringBattery) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = android.net.Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        }
                    }
            ) {
                Text(
                    text = "⚠ Внимание! Система может убить прокси. Нажмите, чтобы отключить оптимизацию.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Console logs
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Терминал", color = TextSecondary, style = MaterialTheme.typography.titleMedium)
                    Row {
                        TextButton(onClick = { 
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("logs", proxyLogs.joinToString("\n"))) 
                        }) {
                            Text("Copy", color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = { viewModel.clearProxyLogs() }) {
                            Text("Clear", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                val listState = rememberLazyListState()
                LaunchedEffect(proxyLogs.size) {
                    if (proxyLogs.isNotEmpty()) {
                        listState.animateScrollToItem(proxyLogs.lastIndex)
                    }
                }

                SelectionContainer(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(proxyLogs) { log ->
                            val isError = log.contains("ОШИБКА", ignoreCase = true) || log.contains("ERROR", ignoreCase = true)
                            val textColor = if (isError) StatusRed else StatusGreen
                            
                            val urlRegex = "(https?://[A-Za-z0-9-._~:/?#\\[\\]@!$&'()*+,;=]+)".toRegex()
                            val matchResult = urlRegex.find(log)
                            
                            if (matchResult != null) {
                                val url = matchResult.value
                                val displayUrl = if (url.length > 60) url.take(55) + "..." else url
                                val start = log.indexOf(url)
                                
                                val annotatedStr = buildAnnotatedString {
                                    append(log.substring(0, start))
                                    withLink(LinkAnnotation.Url(url)) {
                                        withStyle(style = SpanStyle(color = StatusBlue, textDecoration = TextDecoration.Underline)) {
                                            append(displayUrl)
                                        }
                                    }
                                    append(log.substring(start + url.length))
                                }
                                Text(
                                    text = annotatedStr,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = textColor),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            } else {
                                Text(
                                    text = log,
                                    color = textColor,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
