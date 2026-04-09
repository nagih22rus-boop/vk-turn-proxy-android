package com.vkturn.proxy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.concurrent.thread
import com.vkturn.proxy.utils.DnsResolver
import com.vkturn.proxy.models.ClientConfig
import com.google.gson.Gson

class ProxyService : Service() {

    companion object {
        var isRunning = false
        val logBuffer = mutableListOf<String>()
        var onLogReceived: ((String) -> Unit)? = null
        var appContext: Context? = null

        fun addLog(msg: String) {
            if (logBuffer.size > 200) logBuffer.removeAt(0)
            logBuffer.add(msg)
            onLogReceived?.invoke(msg)
        }
        
        fun updateNotification(title: String, text: String) {
            val ctx = appContext ?: return
            val openAppIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
                android.app.PendingIntent.getActivity(ctx, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE)
            }
            val notification = NotificationCompat.Builder(ctx, "ProxyChannel")
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_preferences)
                .setOngoing(true)
                .setContentIntent(openAppIntent)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(1, notification)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var process: Process? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("ProxyChannel", "Proxy", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) return START_STICKY

        appContext = applicationContext
        
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            android.app.PendingIntent.getActivity(this, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE)
        }
        
        val notification = NotificationCompat.Builder(this, "ProxyChannel")
            .setContentTitle("TURN Proxy")
            .setContentText("Работает в фоне")
            .setSmallIcon(android.R.drawable.ic_menu_preferences)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
        isRunning = true

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VkTurn::BgLock")
        wakeLock?.acquire()

        addLog("=== ЗАПУСК ПРОКСИ ===")
        startBinary()

        return START_STICKY
    }

    private fun startBinary() {
        val prefs = getSharedPreferences("ProxyPrefs", Context.MODE_PRIVATE)
        val isRaw = prefs.getBoolean("isRaw", false)

        // Ищем импортированное пользователем ядро
        val customBin = File(filesDir, "libcustom_kernel.so")
        val executable = if (customBin.exists()) {
            customBin.absolutePath
        } else {
            addLog("ОШИБКА: Ядро не импортировано! Пожалуйста, импортируйте ядро на главном экране.")
            isRunning = false
            stopSelf()
            return
        }

        thread {
            try {
                val cmdArgs = mutableListOf<String>()

                if (isRaw) {
                    val rawCmd = prefs.getString("rawCmd", "") ?: ""
                    val parts = rawCmd.trim().split("\\s+".toRegex())
                    cmdArgs.add(executable)
                    if (parts.size > 1) {
                        cmdArgs.addAll(parts.subList(1, parts.size))
                    }
                } else {
                    val gson = Gson()
                    val configJson = prefs.getString("currentConfigJson", null)
                    val config = configJson?.let { gson.fromJson(it, ClientConfig::class.java) } ?: ClientConfig()

                    cmdArgs.add(executable)
                    cmdArgs.add("-peer")
                    
                    val peer = config.serverAddress
                    val link = config.vkLink
                    val n = config.threads.toString()
                    val listen = config.localPort
                    
                    val resolvedPeer = try {
                        if (peer.contains(":")) {
                            val parts = peer.split(":")
                            val host = parts[0]
                            val port = parts[1]
                            val ip = DnsResolver.resolve(host)
                            "$ip:$port"
                        } else {
                            peer
                        }
                    } catch (e: Exception) {
                        peer
                    }
                    
                    cmdArgs.add(resolvedPeer)
                    cmdArgs.add(config.linkArgument)
                    cmdArgs.add(link)
                    cmdArgs.add("-listen")
                    cmdArgs.add(listen)

                    if (n.isNotEmpty() && n != "0") {
                        cmdArgs.add("-n")
                        cmdArgs.add(n)
                    }

                    // Dynamic Flags
                    config.customFlags.forEach { flag ->
                        if (flag.enabled && flag.argument.isNotBlank()) {
                            // Split argument by space in case user put multiple flags in one item
                            flag.argument.trim().split("\\s+".toRegex()).forEach {
                                cmdArgs.add(it)
                            }
                        }
                    }
                }

                addLog("Команда: ${cmdArgs.joinToString(" ")}")

                process = ProcessBuilder(cmdArgs)
                    .redirectErrorStream(true)
                    .start()

                val reader = BufferedReader(InputStreamReader(process?.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    addLog(line ?: "")
                }

                // Если процесс завершился, выводим код
                val exitCode = process?.waitFor()
                addLog("=== ПРОЦЕСС ОСТАНОВЛЕН (Код: $exitCode) ===")
            } catch (e: Exception) {
                addLog("КРИТИЧЕСКАЯ ОШИБКА: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        addLog("=== ОСТАНОВКА ИЗ ИНТЕРФЕЙСА ===")
        
        // Попытка вежливой остановки (SIGINT)
        process?.let { p ->
            thread {
                try {
                    val pid = getProcessPid(p)
                    if (pid != null) {
                        addLog("Отправка сигнала SIGINT (PID: $pid)...")
                        android.os.Process.sendSignal(pid, 2) // 2 = SIGINT
                        
                        // Ждем завершения до 2 секунд
                        var count = 0
                        while (count < 20) { // 20 * 100ms = 2s
                            if (!isProcessAlive(p)) break
                            Thread.sleep(100)
                            count++
                        }
                    }
                    
                    if (isProcessAlive(p)) {
                        addLog("Ядро не ответило вовремя, принудительное завершение...")
                        p.destroy()
                    } else {
                        addLog("Ядро успешно завершило работу.")
                    }
                } catch (e: Exception) {
                    addLog("Ошибка при остановке: ${e.message}")
                    p.destroy()
                }
            }
        }
        
        if (wakeLock?.isHeld == true) wakeLock?.release()
        appContext = null
    }

    private fun getProcessPid(process: Process?): Int? {
        if (process == null) return null
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // В Java 9+ есть нормальный метод, но в Android он появился позже
                // Пробуем через рефлексию (стандартно для UNIXProcess)
                val field = process.javaClass.getDeclaredField("pid")
                field.isAccessible = true
                field.get(process) as Int
            } else {
                val field = process.javaClass.getDeclaredField("pid")
                field.isAccessible = true
                field.get(process) as Int
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isProcessAlive(process: Process?): Boolean {
        return try {
            process?.exitValue()
            false // Если exitValue() не выкинул ошибку, значит процесс завершился
        } catch (e: Exception) {
            true // Означает, что процесс еще работает
        }
    }
}