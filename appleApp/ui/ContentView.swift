import Common
import SwiftUI

struct ContentView: View {
    private let coordinator: TabBarCoordinator

    init(coordinator: TabBarCoordinator) {
        self.coordinator = coordinator
    }

    var body: some View {
        TabBarScreen(coordinator: coordinator)
    }
}

#Preview {
    ContentView(coordinator: AppContainer().tabBarCoordinator)
}
