import Common
import SwiftUI

struct TabBarScreen: View {
    private let coordinator: TabBarCoordinator
    @State private var selectedTab: AppTab
    @State private var alertMessage: String?
    @State private var stopObservingTabs: (() -> Void)?

    init(coordinator: TabBarCoordinator) {
        self.coordinator = coordinator
        _selectedTab = State(
            initialValue: (coordinator.state.value as? TabBarState)?.selectedTab ?? .portfolio
        )
        _alertMessage = State(initialValue:
            (coordinator.state.value as? TabBarState)?.alertMessage)
    }

    var body: some View {
        platformNavigation
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

    @ViewBuilder
    private var platformNavigation: some View {
        #if os(macOS)
        NavigationSplitView {
            List(selection: Binding<AppTab?>(
                get: { selectedTab },
                set: { if let tab = $0 { selection.wrappedValue = tab } }
            )) {
                Label("Портфолио", systemImage: "briefcase").tag(AppTab.portfolio)
                Label("Аналитика", systemImage: "chart.bar").tag(AppTab.analytics)
                Label("Профиль", systemImage: "person").tag(AppTab.profile)
            }
            .navigationSplitViewColumnWidth(min: 170, ideal: 200, max: 260)
        } detail: {
            Group {
                if selectedTab == .portfolio {
                    PortfolioScreen(coordinator: coordinator.portfolioCoordinator)
                        .navigationTitle("Портфолио")
                } else if selectedTab == .analytics {
                    AnalyticsScreen(viewModel: coordinator.analyticsViewModel)
                        .navigationTitle("Аналитика")
                } else {
                    ProfileScreen(viewModel: coordinator.profileViewModel)
                        .navigationTitle("Профиль")
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        #else
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
        #endif
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
    TabBarScreen(coordinator: AppContainer().tabBarCoordinator)
}
