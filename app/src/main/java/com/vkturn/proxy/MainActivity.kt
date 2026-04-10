package com.vkturn.proxy

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.vkturn.proxy.ui.AppNavigation
import com.vkturn.proxy.ui.theme.VkTurnProxyTheme
import com.vkturn.proxy.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // VPN permission request
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            ProxyService.addLog("VPN permission granted!")
        } else {
            ProxyService.addLog("VPN permission denied!")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
        setContent {
            VkTurnProxyTheme {
                AppNavigation(viewModel)
            }
        }
    }

    // Method to request VPN permission - can be called from anywhere
    fun requestVpnPermission() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            ProxyService.addLog("Requesting VPN permission...")
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            ProxyService.addLog("VPN permission already granted")
        }
    }
}
