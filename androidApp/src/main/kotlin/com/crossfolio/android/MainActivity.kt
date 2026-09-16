package com.crossfolio.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.crossfolio.android.network.NetworkManager
import com.crossfolio.android.storage.AndroidProfilePreferencesStorage
import com.crossfolio.android.storage.AndroidProfileSecureStorage
import com.crossfolio.android.ui.navigation.TabBarScreen
import com.crossfolio.common.core.network.ApiKeyInteractor
import com.crossfolio.common.core.network.CoinMarketCapClient
import com.crossfolio.common.navigation.TabBarCoordinator
import com.crossfolio.common.portfolio.PortfolioCoordinator
import com.crossfolio.common.profile.ProfileViewModel
import com.crossfolio.common.portfolio.storage.createAndroidPortfolioStorage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val secureStorage = AndroidProfileSecureStorage(this)
        val transport = NetworkManager()
        val interactor = ApiKeyInteractor(secureStorage, CoinMarketCapClient(transport) { "" })
        val client = CoinMarketCapClient(transport, interactor::getSavedKey)
        val profileViewModel = ProfileViewModel(AndroidProfilePreferencesStorage(this), interactor)
        val portfolioStorage = createAndroidPortfolioStorage(this)
        val coordinator = TabBarCoordinator(
            portfolioCoordinator = PortfolioCoordinator(
                assetCatalog = client,
                imageLoader = client::fetchImg,
                logoUrlProvider = client::logoUrl,
                marketPriceSource = client,
                portfolioStorage = portfolioStorage,
            ),
            profileViewModel = profileViewModel,
            apiKeyInteractor = interactor,
        )
        setContent {
            TabBarScreen(coordinator)
        }
    }
}
