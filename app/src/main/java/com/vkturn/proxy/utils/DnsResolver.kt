package com.vkturn.proxy.utils

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object DnsResolver {
    private const val DNS_SERVER = "77.88.8.8"
    private const val DNS_PORT = 53
    private const val TIMEOUT = 2000

    /**
     * Преобразует хост в IP-адрес. 
     * Сначала пробует системный резолвер, затем напрямую Яндекс.DNS по UDP.
     */
    fun resolve(host: String): String {
        if (host.matches(Regex("^[0-9.]+$"))) return host

        return try {
            // 1. Пробуем системный резолвер
            InetAddress.getByName(host).hostAddress
        } catch (e: Exception) {
            // 2. Если система подвела, идем к Яндексу напрямую
            try {
                val ip = resolveViaUdp(host)
                if (ip == null) {
                    // Мы не логируем тут, чтобы не спамить, если резолвер просто не нашел запись
                }
                ip ?: host
            } catch (ex: Exception) {
                // Если даже UDP запрос упал (например, сеть совсем мертвая)
                host
            }
        }
    }

    private fun resolveViaUdp(host: String): String? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.soTimeout = TIMEOUT
            
            val serverAddress = InetAddress.getByName(DNS_SERVER)
            val transactionId = (0..65535).random()
            val query = buildQuery(host, transactionId)
            val request = DatagramPacket(query, query.size, serverAddress, DNS_PORT)
            
            socket.send(request)
            
            val buffer = ByteArray(512)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            
            parseResponse(buffer, response.length, transactionId)
        } finally {
            socket?.close()
        }
    }

    private fun buildQuery(host: String, transactionId: Int): ByteArray {
        val out = mutableListOf<Byte>()
        // Transaction ID
        out.add((transactionId shr 8).toByte())
        out.add((transactionId and 0xFF).toByte())
        // Flags (Standard query, Recursion desired)
        out.add(0x01.toByte()); out.add(0x00.toByte())
        // Questions count: 1
        out.add(0x00.toByte()); out.add(0x01.toByte())
        // Answer, Authority, Additional RRs (all 0)
        out.add(0x00.toByte()); out.add(0x00.toByte())
        out.add(0x00.toByte()); out.add(0x00.toByte())
        out.add(0x00.toByte()); out.add(0x00.toByte())

        // Labels
        host.split(".").filter { it.isNotEmpty() }.forEach { label ->
            out.add(label.length.toByte())
            label.forEach { out.add(it.code.toByte()) }
        }
        out.add(0x00.toByte())

        // Type A (1), Class IN (1)
        out.add(0x00.toByte()); out.add(0x01.toByte())
        out.add(0x00.toByte()); out.add(0x01.toByte())

        return out.toByteArray()
    }

    private fun parseResponse(data: ByteArray, length: Int, expectedId: Int): String? {
        if (length < 12) return null
        
        // Проверяем Transaction ID
        val id = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        if (id != expectedId) return null
        
        val questions = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        val answers = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        
        if (answers == 0) return null

        var pos = 12
        // Пропускаем вопросы
        for (i in 0 until questions) {
            pos = skipName(data, pos)
            pos += 4 // Type + Class
        }

        // Ищем первую живую A-запись
        for (i in 0 until answers) {
            pos = skipName(data, pos)
            val type = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos+1].toInt() and 0xFF)
            val rdLength = ((data[pos+8].toInt() and 0xFF) shl 8) or (data[pos+9].toInt() and 0xFF)
            pos += 10
            
            if (type == 1 && rdLength == 4 && pos + 4 <= length) {
                return "${data[pos].toInt() and 0xFF}.${data[pos+1].toInt() and 0xFF}.${data[pos+2].toInt() and 0xFF}.${data[pos+3].toInt() and 0xFF}"
            }
            pos += rdLength
        }
        return null
    }

    private fun skipName(data: ByteArray, startPos: Int): Int {
        var pos = startPos
        while (pos < data.size) {
            val v = data[pos].toInt() and 0xFF
            if (v == 0) return pos + 1
            if ((v and 0xC0) == 0xC0) return pos + 2 // Сжатие (Pointer)
            pos += v + 1
        }
        return pos
    }
}
