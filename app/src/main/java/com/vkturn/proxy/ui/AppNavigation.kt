package com.vkturn.proxy.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vkturn.proxy.viewmodel.MainViewModel
import com.vkturn.proxy.ui.components.CurrentProfileHeader
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "home"

    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val selectedProfileId by viewModel.selectedProfileId.collectAsStateWithLifecycle()
    
    val currentProfileName = remember(profiles, selectedProfileId) {
        profiles.find { it.id == selectedProfileId }?.name ?: "Не выбран"
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == "home",
                    onClick = { navController.navigate("home") { launchSingleTop = true } },
                    icon = { Text("🏠") },
                    label = { Text("Главная") }
                )
                NavigationBarItem(
                    selected = currentRoute == "settings",
                    onClick = { navController.navigate("settings") { launchSingleTop = true } },
                    icon = { Text("⚙️") },
                    label = { Text("Настройки") }
                )
                NavigationBarItem(
                    selected = currentRoute == "ssh",
                    onClick = { navController.navigate("ssh") { launchSingleTop = true } },
                    icon = { Text("🖥️") },
                    label = { Text("Сервер") }
                )
            }
        },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                CurrentProfileHeader(profileName = currentProfileName)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(viewModel)
            }
            composable("settings") {
                SettingsScreen(viewModel)
            }
            composable("ssh") {
                SshScreen(viewModel)
            }
        }
    }
}
