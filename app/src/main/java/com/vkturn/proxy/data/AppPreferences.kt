package com.vkturn.proxy.data

import android.content.Context
import android.content.SharedPreferences
import com.vkturn.proxy.models.ProxyProfile
import com.vkturn.proxy.models.ClientConfig
import com.vkturn.proxy.models.ProxyFlag
import com.vkturn.proxy.models.SshConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppPreferences(context: Context) {
    private val proxyPrefs: SharedPreferences = context.getSharedPreferences("ProxyPrefs", Context.MODE_PRIVATE)
    private val sshPrefs: SharedPreferences = context.getSharedPreferences("SshPrefs", Context.MODE_PRIVATE)

    private val _clientConfigFlow = MutableStateFlow(loadClientConfig())
    val clientConfigFlow: StateFlow<ClientConfig> = _clientConfigFlow.asStateFlow()

    private val _sshConfigFlow = MutableStateFlow(loadSshConfig())
    val sshConfigFlow: StateFlow<SshConfig> = _sshConfigFlow.asStateFlow()

    private val gson = Gson()

    private val _selectedProfileIdFlow = MutableStateFlow(proxyPrefs.getString("selectedProfileId", "") ?: "")
    val selectedProfileIdFlow: StateFlow<String> = _selectedProfileIdFlow.asStateFlow()

    private val _profilesFlow = MutableStateFlow(loadProfiles())
    val profilesFlow: StateFlow<List<ProxyProfile>> = _profilesFlow.asStateFlow()

    companion object {
        fun migrateConfig(config: ClientConfig): ClientConfig {
            var currentFlags = (config.customFlags ?: emptyList()).toMutableList()
            var changed = false

            if (currentFlags.isEmpty()) {
                currentFlags = mutableListOf(
                    ProxyFlag(label = "UDP", argument = "-udp", enabled = true, deletable = false),
                    ProxyFlag(label = "VLESS", argument = "-vless", enabled = false, deletable = false),
                    ProxyFlag(label = "Manual Captcha", argument = "--manual-captcha", enabled = false, deletable = false),
                    ProxyFlag(label = "Disable DTLS", argument = "-no-dtls", enabled = false, deletable = false),
                    ProxyFlag(label = "Debug", argument = "-debug", enabled = false, deletable = false)
                )
                changed = true
            } else {
                val mandatoryArgs = listOf("-udp", "-vless", "--manual-captcha", "-no-dtls", "-debug")
                val mandatoryLabels = mapOf(
                    "-udp" to "UDP",
                    "-vless" to "VLESS",
                    "--manual-captcha" to "Manual Captcha",
                    "-no-dtls" to "Disable DTLS",
                    "-debug" to "Debug"
                )

                // 1. Update labels and identify present mandatory flags
                mandatoryArgs.forEach { arg ->
                    val label = mandatoryLabels[arg]!!
                    val existing = currentFlags.find { it.argument == arg }
                    if (existing == null) {
                        currentFlags.add(ProxyFlag(label = label, argument = arg, enabled = false, deletable = false))
                        changed = true
                    } else if (existing.label != label) {
                        val idx = currentFlags.indexOf(existing)
                        currentFlags[idx] = existing.copy(label = label)
                        changed = true
                    }
                }

                // 2. Reorder: Mandatory flags first in the specified order, then the rest
                val orderedFlags = mutableListOf<ProxyFlag>()
                mandatoryArgs.forEach { arg ->
                    currentFlags.find { it.argument == arg }?.let { orderedFlags.add(it) }
                }
                val otherFlags = currentFlags.filter { it.argument !in mandatoryArgs }
                
                if (currentFlags != (orderedFlags + otherFlags)) {
                    currentFlags.clear()
                    currentFlags.addAll(orderedFlags)
                    currentFlags.addAll(otherFlags)
                    changed = true
                }
            }

            // Ensure linkArgument is valid ONLY if link is present
            var linkArg = config.linkArgument
            if (linkArg.isEmpty() && config.vkLink.isNotEmpty()) {
                linkArg = if (config.vkLink.contains("yandex")) "-yandex-link" else "-vk-link"
                changed = true
            }

            // Ensure isRawMode is false if rawCommand is empty (safety for legacy profiles)
            if (config.isRawMode && config.rawCommand.isBlank()) {
                return config.copy(
                    customFlags = currentFlags, 
                    linkArgument = linkArg, 
                    isRawMode = false
                )
            }

            return if (changed) config.copy(customFlags = currentFlags, linkArgument = linkArg) else config
        }
    }

    private fun loadClientConfig(): ClientConfig {
        val json = proxyPrefs.getString("currentConfigJson", null)
        if (json != null) {
            return try {
                val config = gson.fromJson(json, ClientConfig::class.java)
                migrateConfig(config)
            } catch (e: Exception) {
                createDefaultConfig()
            }
        }
        return createDefaultConfig()
    }

    fun createDefaultConfig(): ClientConfig {
        val udp = proxyPrefs.getBoolean("udp", true)
        val noDtls = proxyPrefs.getBoolean("noDtls", false)
        val captcha = proxyPrefs.getBoolean("useManualCaptcha", false)
        val link = proxyPrefs.getString("link", "") ?: ""
        val linkArg = proxyPrefs.getString("linkArg", "") ?: ""

        val flags = listOf(
            ProxyFlag(label = "UDP", argument = "-udp", enabled = udp, deletable = false),
            ProxyFlag(label = "VLESS", argument = "-vless", enabled = false, deletable = false),
            ProxyFlag(label = "Manual Captcha", argument = "--manual-captcha", enabled = false, deletable = false),
            ProxyFlag(label = "Disable DTLS", argument = "-no-dtls", enabled = noDtls, deletable = false),
            ProxyFlag(label = "Debug", argument = "-debug", enabled = false, deletable = false)
        )

        return ClientConfig(
            serverAddress = proxyPrefs.getString("peer", "") ?: "",
            vkLink = link,
            linkArgument = linkArg,
            threads = proxyPrefs.getString("n", "8")?.toIntOrNull() ?: 8,
            localPort = proxyPrefs.getString("listen", "127.0.0.1:9000") ?: "127.0.0.1:9000",
            isRawMode = false, // Always default to false for new/default configs
            rawCommand = "",
            customFlags = flags
        )
    }

    private fun loadSshConfig(): SshConfig {
        return SshConfig(
            ip = sshPrefs.getString("ip", "") ?: "",
            port = sshPrefs.getString("port", "22")?.toIntOrNull() ?: 22,
            username = sshPrefs.getString("user", "root") ?: "root",
            password = sshPrefs.getString("pass", "") ?: "",
            proxyListen = sshPrefs.getString("proxyListen", "0.0.0.0:56000") ?: "0.0.0.0:56000",
            proxyConnect = sshPrefs.getString("proxyConnect", "127.0.0.1:40537") ?: "127.0.0.1:40537"
        )
    }

    fun saveClientConfig(config: ClientConfig) {
        proxyPrefs.edit().apply {
            putString("peer", config.serverAddress)
            putString("link", config.vkLink)
            putString("linkArg", config.linkArgument)
            putString("n", config.threads.toString())
            putString("listen", config.localPort)
            putBoolean("isRaw", config.isRawMode)
            putString("rawCmd", config.rawCommand)
            putString("currentConfigJson", gson.toJson(config))
        }.apply()
        _clientConfigFlow.value = config
    }

    fun saveSshConfig(config: SshConfig) {
        sshPrefs.edit().apply {
            putString("ip", config.ip)
            putString("port", config.port.toString())
            putString("user", config.username)
            putString("pass", config.password)
            putString("proxyListen", config.proxyListen)
            putString("proxyConnect", config.proxyConnect)
        }.apply()
        _sshConfigFlow.value = config
    }

    fun loadProfiles(): List<ProxyProfile> {
        val json = proxyPrefs.getString("profilesJson", null)
        if (json == null) {
            val initialProfile = ProxyProfile(name = "По умолчанию", config = loadClientConfig(), isDefault = true)
            val profiles = listOf(initialProfile)
            proxyPrefs.edit().putString("profilesJson", gson.toJson(profiles)).apply()
            if (proxyPrefs.getString("selectedProfileId", "").isNullOrEmpty()) {
                proxyPrefs.edit().putString("selectedProfileId", initialProfile.id).apply()
            }
            return profiles
        }
        val type = object : TypeToken<List<ProxyProfile>>() {}.type
        val rawProfiles: List<ProxyProfile> = gson.fromJson(json, type) ?: emptyList()
        
        val migratedProfiles = rawProfiles.map { profile ->
            // Safety: if it's named "По умолчанию" but lacks the flag (legacy), set it.
            val isDefault = profile.isDefault || profile.name == "По умолчанию"
            profile.copy(config = migrateConfig(profile.config), isDefault = isDefault)
        }

        if (migratedProfiles != rawProfiles) {
            proxyPrefs.edit().putString("profilesJson", gson.toJson(migratedProfiles)).apply()
        }
        
        return migratedProfiles
    }

    fun saveProfiles(profiles: List<ProxyProfile>) {
        proxyPrefs.edit().putString("profilesJson", gson.toJson(profiles)).apply()
        _profilesFlow.value = profiles
    }

    fun saveSelectedProfileId(id: String) {
        proxyPrefs.edit().putString("selectedProfileId", id).apply()
        _selectedProfileIdFlow.value = id
    }
}
