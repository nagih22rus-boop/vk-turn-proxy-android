package com.vkturn.proxy.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.vkturn.proxy.utils.QrUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun QrExportDialog(
    profileName: String,
    profileData: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Brightness control
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val layoutParams = window?.attributes
        val originalBrightness = layoutParams?.screenBrightness
        
        layoutParams?.screenBrightness = 1.0f // Max brightness
        window?.attributes = layoutParams
        
        onDispose {
            layoutParams?.screenBrightness = originalBrightness ?: -1.0f
            window?.attributes = layoutParams
        }
    }

    LaunchedEffect(profileData) {
        withContext(Dispatchers.Default) {
            qrBitmap = QrUtils.generateQrCode(profileData, context = context)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Экспорт профиля",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = profileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(250.dp)
                            .background(Color.White)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("vk_proxy_profile", profileData))
                                        Toast.makeText(context, "Скопировано в буфер обмена", Toast.LENGTH_SHORT).show()
                                    },
                                    onLongPress = {
                                        shareBitmap(context, qrBitmap!!, profileName)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "QR-код",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Нажмите, чтобы скопировать\nУдерживайте, чтобы поделиться",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Закрыть")
                }
            }
        }
    }
}

private fun shareBitmap(context: Context, bitmap: Bitmap, profileName: String) {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "profile_qr.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Профиль VK Proxy: $profileName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться профилем"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Ошибка подготовки изображения", Toast.LENGTH_SHORT).show()
    }
}
