import Common
import SwiftUI

struct TabBarScreen: View {
    private let coordinator: TabBarCoordinator
    @State private var selectedTab: AppTab
    @State private var alertMessage: String?
    @State private var stopObservingTabs: (() -> Void)?

    init(coordinator: TabBarCoordinator? = nil) {
        let coordinator = coordinator ?? Self.makeCoordinator()
        self.coordinator = coordinator
        _selectedTab = State(
            initialValue: (coordinator.state.value as? TabBarState)?.selectedTab ?? .portfolio
        )
        _alertMessage = State(initialValue:
            (coordinator.state.value as? TabBarState)?.alertMessage)
    }

    private static func makeCoordinator() -> TabBarCoordinator {
        let secureStorage = AppleProfileSecureStorage()
        let transport = NetworkManager()
        let manager = ApiKeyManager(secureStorage: secureStorage,
            validator: ApiKeyValidator(validation: CoinMarketCapClient(transport: transport, apiKeyProvider: { "" })))
        let client = CoinMarketCapClient(transport: transport, apiKeyProvider: {
            manager.getSavedKey()
        })
        let profileViewModel = ProfileViewModel(
            preferencesStorage: AppleProfilePreferencesStorage(), apiKeyManager: manager)
        return TabBarCoordinator(
            portfolioCoordinator: PortfolioCoordinator(
                assetCatalog: client,
                imageLoader: { url, completion in
                    client.fetchImg(url: url) { result in _ = completion(result) }
                }
            ),
            analyticsViewModel: AnalyticsViewModel(),
            profileViewModel: profileViewModel,
            apiKeyManager: manager
        )
    }

    var body: some View {
        TabView(selection: selection) {
            PortfolioScreen(coordinator: coordinator.portfolioCoordinator)
                .tabItem { Label("Портфолио", systemImage: "briefcase") }
                .tag(AppTab.portfolio)

            AnalyticsScreen(viewModel: coordinator.analyticsViewModel)
                .tabItem { Label("Аналитика", systemImage: "chart.bar") }
                .tag(AppTab.analytics)

            ProfileScreen(viewModel: coordinator.profileViewModel)
                .tabItem { Label("Профиль", systemImage: "person") }
                .tag(AppTab.profile)
        }
        .onAppear {
            stopObservingTabs?()
            stopObservingTabs = coordinator.observeState {
                selectedTab = $0.selectedTab
                alertMessage = $0.alertMessage
            }
        }
        .onDisappear {
            stopObservingTabs?()
            stopObservingTabs = nil
        }
        .alert("Ошибка", isPresented: alertPresented) {
            Button("ОК") { coordinator.dismissAlert() }
        } message: {
            Text(alertMessage ?? "")
        }
    }

    private var alertPresented: Binding<Bool> {
        Binding(
            get: { alertMessage != nil },
            set: { presented in
                if !presented {
                    coordinator.dismissAlert()
                    alertMessage = nil
                }
            }
        )
    }

    private var selection: Binding<AppTab> {
        Binding(
            get: { selectedTab },
            set: { tab in
                coordinator.selectTab(tab: tab)
                selectedTab = tab
            }
        )
    }
}

#Preview {
    TabBarScreen()
}
