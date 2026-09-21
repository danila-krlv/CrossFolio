import Common
import SwiftUI

struct ContentView: View {
    private let coordinator: TabBarCoordinator
    @State private var theme: AppTheme
    @State private var stopObservingProfile: (() -> Void)?

    init(coordinator: TabBarCoordinator) {
        self.coordinator = coordinator
        _theme = State(initialValue: (coordinator.profileViewModel.state.value as! ProfileState).theme)
    }

    var body: some View {
        TabBarScreen(coordinator: coordinator)
            .preferredColorScheme(theme == .system ? nil : theme == .dark ? .dark : .light)
            .onAppear {
                stopObservingProfile?()
                stopObservingProfile = coordinator.profileViewModel.observeState { theme = $0.theme }
            }
            .onDisappear {
                stopObservingProfile?()
                stopObservingProfile = nil
            }
    }
}

#Preview {
    ContentView(coordinator: AppContainer().tabBarCoordinator)
}
