package com.crossfolio.android.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.crossfolio.android.ui.analytics.AnalyticsScreen
import com.crossfolio.android.ui.portfolio.PortfolioScreen
import com.crossfolio.android.ui.profile.ProfileScreen
import com.crossfolio.common.core.network.ApiKeyValidationStatus
import com.crossfolio.common.navigation.AppTab
import com.crossfolio.common.navigation.TabBarCoordinator

@Composable
fun TabBarScreen(
    coordinator: TabBarCoordinator = remember { TabBarCoordinator() },
) {
    val state by coordinator.state.collectAsState()

    MaterialTheme {
        state.alertMessage?.let { message ->
            AlertDialog(
                onDismissRequest = coordinator::dismissAlert,
                title = { Text("Ошибка") },
                text = { Text(message) },
                dismissButton = {
                    if (state.apiKeyValidation.status == ApiKeyValidationStatus.CHECK_FAILED) {
                        TextButton(onClick = coordinator.apiKeyInteractor::start) { Text("Проверить сохранённый ключ") }
                    }
                },
                confirmButton = {
                    TextButton(onClick = coordinator::dismissAlert) { Text("ОК") }
                },
            )
        }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = state.selectedTab == tab,
                            onClick = { coordinator.selectTab(tab) },
                            icon = {},
                            label = { Text(tab.title) },
                        )
                    }
                }
            },
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                when (state.selectedTab) {
                    AppTab.PORTFOLIO -> PortfolioScreen(coordinator.portfolioCoordinator)
                    AppTab.ANALYTICS -> AnalyticsScreen(coordinator.analyticsViewModel)
                    AppTab.PROFILE -> ProfileScreen(coordinator.profileViewModel)
                }
            }
        }
    }
}

private val AppTab.title: String
    get() = when (this) {
        AppTab.PORTFOLIO -> "Портфолио"
        AppTab.ANALYTICS -> "Аналитика"
        AppTab.PROFILE -> "Профиль"
    }

@Preview(showBackground = true)
@Composable
private fun TabBarScreenPreview() {
    TabBarScreen()
}
