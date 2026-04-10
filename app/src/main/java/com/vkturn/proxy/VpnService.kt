package com.vkturn.proxy

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class VpnService : VpnService() {

    companion object {
        var isRunning = false
        var onLogReceived: ((String) -> Unit)? = null
        private var vpnInterface: ParcelFileDescriptor? = null
        
        // Local proxy port - will be set from ProxyService
        var proxyPort: Int = 9000
        
        fun addLog(msg: String) {
            onLogReceived?.invoke(msg)
        }
        
        fun stopVpn() {
            try {
                vpnInterface?.close()
                vpnInterface = null
            } catch (e: Exception) {
                addLog("Error closing VPN: ${e.message}")
            }
        }
    }

    private val isActive = AtomicBoolean(false)
    private var vpnThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        addLog("VpnService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        addLog("VpnService onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            "START" -> startVpn()
            "STOP" -> stopVpnService()
            else -> addLog("Unknown action: ${intent?.action}")
        }
        return START_STICKY
    }

    private fun startVpn() {
        addLog("startVpn() called, isActive=${isActive.get()}")
        
        if (isActive.get()) {
            addLog("VPN already running")
            return
        }

        try {
            // Get proxy port from preferences
            val prefs = getSharedPreferences("ProxyPrefs", Context.MODE_PRIVATE)
            val localPort = prefs.getString("localPort", "9000") ?: "9000"
            proxyPort = localPort.split(":").lastOrNull()?.toIntOrNull() ?: 9000
            
            addLog("Starting VPN with proxy port: $proxyPort")
            addLog("Checking VPN permission...")
            
            // Check if VPN permission is granted
            val prepare = VpnService.prepare(this)
            if (prepare != null) {
                addLog("VPN permission required! Activity: ${prepare.component}")
                // Need to request permission - this should be handled by the caller
                addLog("ERROR: VPN permission not granted. Please allow VPN in system dialog.")
                return
            }
            addLog("VPN permission OK")

            // Build VPN interface
            val builder = Builder()
                .setSession("VK TURN VPN")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .setMtu(1500)
                .setBlocking(true)

            // Allow app to bypass VPN
            builder.addDisallowedApplication(packageName)

            vpnInterface = builder.establish()
            
            if (vpnInterface == null) {
                addLog("Failed to establish VPN interface")
                return
            }

            isActive.set(true)
            isRunning = true
            
            // Start processing packets
            vpnThread = Thread {
                processPackets()
            }.apply {
                name = "VpnServiceThread"
                start()
            }

            addLog("VPN started successfully")
            
            // Update notification
            updateNotification("VPN Active", "Traffic is being routed")

        } catch (e: Exception) {
            addLog("Error starting VPN: ${e.message}")
            e.printStackTrace()
            stopVpnService()
        }
    }

    private fun processPackets() {
        val vpnFd = vpnInterface ?: return
        val inputStream = FileInputStream(vpnFd.fileDescriptor)
        val outputStream = FileOutputStream(vpnFd.fileDescriptor)
        
        // Buffer for reading packets from VPN
        val packetBuffer = ByteBuffer.allocate(32767)
        
        // Buffer for proxy communication
        val proxyBuffer = ByteArray(32767)
        
        // Create UDP socket to connect to local proxy
        var proxySocket: DatagramSocket? = null
        
        try {
            proxySocket = DatagramSocket()
            proxySocket.connect(InetAddress.getByName("127.0.0.1"), proxyPort)
            proxySocket.soTimeout = 0 // No timeout
            
            addLog("Connected to proxy at 127.0.0.1:$proxyPort")
            
            while (isActive.get()) {
                try {
                    // Read packet from VPN interface
                    packetBuffer.clear()
                    val length = inputStream.read(packetBuffer.array())
                    
                    if (length > 0) {
                        packetBuffer.limit(length)
                        
                        // Extract destination from IP packet
                        val destIp = extractDestinationIp(packetBuffer.array(), length)
                        val destPort = extractDestinationPort(packetBuffer.array(), length)
                        
                        if (destIp != null && destPort != null) {
                            // Forward to proxy
                            val proxyPacket = DatagramPacket(
                                packetBuffer.array(), 
                                length,
                                InetAddress.getByName("127.0.0.1"),
                                proxyPort
                            )
                            proxySocket.send(proxyPacket)
                            
                            // Read response from proxy
                            val responsePacket = DatagramPacket(proxyBuffer, proxyBuffer.size)
                            try {
                                proxySocket.receive(responsePacket)
                                
                                // Write response back to VPN
                                outputStream.write(proxyBuffer, 0, responsePacket.length)
                                outputStream.flush()
                            } catch (e: Exception) {
                                // Timeout or no response - that's ok for UDP
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive.get()) {
                        addLog("Packet processing error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            addLog("VPN thread error: ${e.message}")
        } finally {
            try {
                inputStream.close()
                outputStream.close()
                proxySocket?.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
            addLog("VPN packet processing stopped")
        }
    }

    private fun extractDestinationIp(packet: ByteArray, length: Int): String? {
        if (length < 20) return null
        
        // IPv4: byte 12-15 is destination IP
        val version = (packet[0].toInt() shr 4) and 0xF
        if (version != 4) return null // Only IPv4 for now
        
        return "${packet[16].toInt() and 0xFF}.${packet[17].toInt() and 0xFF}.${packet[18].toInt() and 0xFF}.${packet[19].toInt() and 0xFF}"
    }

    private fun extractDestinationPort(packet: ByteArray, length: Int): Int? {
        if (length < 24) return null
        
        val version = (packet[0].toInt() shr 4) and 0xF
        if (version != 4) return null
        
        // TCP/UDP destination port is at offset 20-21 (after IP header)
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 6 && protocol != 17) return null // Only TCP and UDP
        
        val headerLength = (packet[0].toInt() and 0xF) * 4
        if (length < headerLength + 2) return null
        
        return ((packet[headerLength + 2].toInt() and 0xFF) shl 8) or (packet[headerLength + 3].toInt() and 0xFF)
    }

    private fun stopVpnService() {
        addLog("Stopping VPN...")
        isActive.set(false)
        isRunning = false
        
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            addLog("Error closing VPN: ${e.message}")
        }
        
        vpnThread?.interrupt()
        vpnThread = null
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        addLog("VPN stopped")
    }

    override fun onDestroy() {
        stopVpnService()
        super.onDestroy()
    }

    override fun onRevoke() {
        addLog("VPN permission revoked")
        stopVpnService()
        super.onRevoke()
    }

    private fun updateNotification(title: String, text: String) {
        try {
            val notification = android.app.Notification.Builder(this, "ProxyChannel")
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_preferences)
                .setOngoing(true)
                .build()
            
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.notify(2, notification)
        } catch (e: Exception) {
            // Ignore notification errors
        }
    }
}