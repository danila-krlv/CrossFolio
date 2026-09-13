import Common
import SwiftUI

struct TabBarScreen: View {
    private let coordinator: TabBarCoordinator
    @State private var selectedTab: AppTab

    init(coordinator: TabBarCoordinator? = nil) {
        let coordinator = coordinator ?? Self.makeCoordinator()
        self.coordinator = coordinator
        _selectedTab = State(
            initialValue: (coordinator.state.value as? TabBarState)?.selectedTab ?? .portfolio
        )
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
            portfolioCoordinator: PortfolioCoordinator(networkManager: networkManager),
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
