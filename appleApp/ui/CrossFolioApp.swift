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

        let portfolioCoordinator = PortfolioCoordinator(
            assetCatalog: client,
            imageLoader: { url, completion in
                client.fetchImg(url: url) { result in _ = completion(result) }
            },
            logoUrlProvider: { asset in client.logoUrl(asset: asset) },
            marketPriceSource: client,
            portfolioStorage: portfolioStorage
        )
        tabBarCoordinator = TabBarCoordinator(
            portfolioCoordinator: portfolioCoordinator,
            analyticsViewModel: AnalyticsViewModel(
                portfolioViewModel: portfolioCoordinator.portfolioViewModel,
                portfolioStorage: portfolioStorage
            ),
            profileViewModel: profileViewModel,
            apiKeyInteractor: interactor
        )
    }
}

@main
struct CrossFolioApp: App {
    private let appContainer = AppContainer()

    var body: some Scene {
        #if os(macOS)
        Window("CrossFolio", id: "main") {
            ContentView(coordinator: appContainer.tabBarCoordinator)
                .frame(minWidth: 720, minHeight: 520)
        }
        .defaultSize(width: 1000, height: 700)
        .windowResizability(.contentMinSize)
        #else
        WindowGroup {
            ContentView(coordinator: appContainer.tabBarCoordinator)
        }
        #endif
    }
}
