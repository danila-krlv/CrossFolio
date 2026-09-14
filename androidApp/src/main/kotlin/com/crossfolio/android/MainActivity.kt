package com.crossfolio.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.crossfolio.android.network.NetworkManager
import com.crossfolio.android.storage.AndroidProfilePreferencesStorage
import com.crossfolio.android.storage.AndroidProfileSecureStorage
import com.crossfolio.android.ui.navigation.TabBarScreen
import com.crossfolio.common.navigation.TabBarCoordinator
import com.crossfolio.common.portfolio.PortfolioCoordinator
import com.crossfolio.common.profile.ProfileViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileViewModel = ProfileViewModel(
            preferencesStorage = AndroidProfilePreferencesStorage(this),
            secureStorage = AndroidProfileSecureStorage(this),
        )
        val coordinator = TabBarCoordinator(
            portfolioCoordinator = PortfolioCoordinator(
                networkManager = NetworkManager {
                    profileViewModel.getCoinMarketCapApiKey()
                },
                apiKeyProvider = profileViewModel::getCoinMarketCapApiKey,
            ),
            profileViewModel = profileViewModel,
        )
        setContent {
            TabBarScreen(coordinator)
        }
    }
}
