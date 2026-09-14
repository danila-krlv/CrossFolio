package com.crossfolio.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.crossfolio.android.network.NetworkManager
import com.crossfolio.android.storage.AndroidProfilePreferencesStorage
import com.crossfolio.android.storage.AndroidProfileSecureStorage
import com.crossfolio.android.ui.navigation.TabBarScreen
import com.crossfolio.common.core.network.ApiKeyManager
import com.crossfolio.common.core.network.ApiKeyValidator
import com.crossfolio.common.core.network.CoinMarketCapClient
import com.crossfolio.common.navigation.TabBarCoordinator
import com.crossfolio.common.portfolio.PortfolioCoordinator
import com.crossfolio.common.profile.ProfileViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val secureStorage = AndroidProfileSecureStorage(this)
        val transport = NetworkManager()
        val manager = ApiKeyManager(secureStorage, ApiKeyValidator(transport))
        val client = CoinMarketCapClient(transport, manager::getSavedKey)
        val profileViewModel = ProfileViewModel(AndroidProfilePreferencesStorage(this), manager)
        val coordinator = TabBarCoordinator(
            portfolioCoordinator = PortfolioCoordinator(
                networkManager = client,
            ),
            profileViewModel = profileViewModel,
            apiKeyManager = manager,
        )
        setContent {
            TabBarScreen(coordinator)
        }
    }
}
