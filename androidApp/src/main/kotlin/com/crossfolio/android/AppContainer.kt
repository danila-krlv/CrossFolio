package com.crossfolio.android

import android.app.Application
import android.content.Context
import com.crossfolio.android.network.NetworkManager
import com.crossfolio.android.storage.AndroidProfilePreferencesStorage
import com.crossfolio.android.storage.AndroidProfileSecureStorage
import com.crossfolio.common.core.network.ApiKeyInteractor
import com.crossfolio.common.core.network.CoinMarketCapClient
import com.crossfolio.common.navigation.TabBarCoordinator
import com.crossfolio.common.portfolio.PortfolioCoordinator
import com.crossfolio.common.portfolio.storage.createAndroidPortfolioStorage
import com.crossfolio.common.profile.ProfileViewModel

internal class AppContainer(context: Context) {
    val tabBarCoordinator: TabBarCoordinator

    init {
        val applicationContext = context.applicationContext
        val secureStorage = AndroidProfileSecureStorage(applicationContext)
        val transport = NetworkManager()
        val interactor = ApiKeyInteractor(secureStorage, CoinMarketCapClient(transport) { "" })
        val client = CoinMarketCapClient(transport, interactor::getSavedKey)
        val imageClient = CoinMarketCapClient(NetworkManager.forImages()) { "" }
        val profileViewModel = ProfileViewModel(
            AndroidProfilePreferencesStorage(applicationContext),
            interactor,
        )
        val portfolioStorage = createAndroidPortfolioStorage(applicationContext)

        tabBarCoordinator = TabBarCoordinator(
            portfolioCoordinator = PortfolioCoordinator(
                assetCatalog = client,
                imageLoader = imageClient::fetchImg,
                logoUrlProvider = client::logoUrl,
                marketPriceSource = client,
                portfolioStorage = portfolioStorage,
            ),
            profileViewModel = profileViewModel,
            apiKeyInteractor = interactor,
        )
    }
}

class CrossFolioApplication : Application() {
    internal lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
