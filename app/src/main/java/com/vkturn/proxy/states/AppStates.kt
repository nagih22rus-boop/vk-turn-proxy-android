package com.vkturn.proxy.states

sealed class ProxyState {
    object Idle : ProxyState()
    object Starting : ProxyState()
    object Running : ProxyState()
    data class Error(val message: String) : ProxyState()
    data class CaptchaRequired(val url: String) : ProxyState()
}

sealed class SshConnectionState {
    object Disconnected : SshConnectionState()
    object Connecting : SshConnectionState()
    data class Connected(val ip: String) : SshConnectionState()
    data class Error(val message: String) : SshConnectionState()
}
