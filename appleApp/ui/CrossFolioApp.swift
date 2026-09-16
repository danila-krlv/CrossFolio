import Common
import SwiftUI

final class AppContainer {
    let tabBarCoordinator: TabBarCoordinator

    init() {
        let secureStorage = AppleProfileSecureStorage()
        let transport = NetworkManager()
        let interactor = ApiKeyInteractor(
            secureStorage: secureStorage,
            validation: CoinMarketCapClient(transport: transport, apiKeyProvider: { "" })
        )
        let client = CoinMarketCapClient(transport: transport, apiKeyProvider: {
            interactor.getSavedKey()
        })
        let profileViewModel = ProfileViewModel(
            preferencesStorage: AppleProfilePreferencesStorage(),
            apiKeyInteractor: interactor
        )
        let portfolioStorage = ApplePortfolioStorageKt.createApplePortfolioStorage()

        tabBarCoordinator = TabBarCoordinator(
            portfolioCoordinator: PortfolioCoordinator(
                assetCatalog: client,
                imageLoader: { url, completion in
                    client.fetchImg(url: url) { result in _ = completion(result) }
                },
                logoUrlProvider: { asset in client.logoUrl(asset: asset) },
                marketPriceSource: client,
                portfolioStorage: portfolioStorage
            ),
            analyticsViewModel: AnalyticsViewModel(),
            profileViewModel: profileViewModel,
            apiKeyInteractor: interactor
        )
    }
}

@main
struct CrossFolioApp: App {
    private let appContainer = AppContainer()

    var body: some Scene {
        WindowGroup {
            ContentView(coordinator: appContainer.tabBarCoordinator)
        }
    }
}
