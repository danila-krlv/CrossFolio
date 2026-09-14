import Common
import SwiftUI

struct TabBarScreen: View {
    private let coordinator: TabBarCoordinator
    @State private var selectedTab: AppTab
    @State private var alertMessage: String?
    @State private var stopObserving: (() -> Void)?
    @State private var stopObservingTabs: (() -> Void)?

    init(coordinator: TabBarCoordinator? = nil) {
        let coordinator = coordinator ?? Self.makeCoordinator()
        self.coordinator = coordinator
        _selectedTab = State(
            initialValue: (coordinator.state.value as? TabBarState)?.selectedTab ?? .portfolio
        )
        _alertMessage = State(initialValue:
            (coordinator.portfolioCoordinator.state.value as? PortfolioNavigationState)?.alertMessage)
    }

    private static func makeCoordinator() -> TabBarCoordinator {
        let profileViewModel = ProfileViewModel(
            preferencesStorage: AppleProfilePreferencesStorage(),
            secureStorage: AppleProfileSecureStorage()
        )
        let networkManager = NetworkManager(apiKeyProvider: {
            profileViewModel.getCoinMarketCapApiKey()
        })
        return TabBarCoordinator(
            portfolioCoordinator: PortfolioCoordinator(
                networkManager: networkManager,
                apiKeyProvider: { profileViewModel.getCoinMarketCapApiKey() }
            ),
            analyticsViewModel: AnalyticsViewModel(),
            profileViewModel: profileViewModel
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
            stopObserving?()
            stopObserving = coordinator.portfolioCoordinator.observeState { alertMessage = $0.alertMessage }
            stopObservingTabs?()
            stopObservingTabs = coordinator.observeState { selectedTab = $0.selectedTab }
        }
        .onDisappear {
            stopObserving?()
            stopObserving = nil
            stopObservingTabs?()
            stopObservingTabs = nil
        }
        .alert("API-ключ CoinMarketCap", isPresented: alertPresented) {
            Button("ОК") { coordinator.portfolioCoordinator.dismissAlert() }
        } message: {
            Text(alertMessage ?? "")
        }
    }

    private var alertPresented: Binding<Bool> {
        Binding(
            get: { alertMessage != nil },
            set: { presented in
                if !presented {
                    coordinator.portfolioCoordinator.dismissAlert()
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
