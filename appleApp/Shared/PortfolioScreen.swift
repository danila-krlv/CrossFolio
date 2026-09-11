import Shared
import SwiftUI

struct PortfolioScreen: View {
    private let coordinator: PortfolioCoordinator
    @State private var currentRoute: PortfolioRoute

    init(coordinator: PortfolioCoordinator) {
        self.coordinator = coordinator
        _currentRoute = State(
            initialValue: (coordinator.state.value as? PortfolioNavigationState)?.currentRoute
                ?? .portfolio
        )
    }

    var body: some View {
        if currentRoute == .portfolio {
            portfolioContent
        } else {
            assetSearchContent
        }
    }

    private var portfolioContent: some View {
        ZStack(alignment: .bottomTrailing) {
            Text(portfolioState.message)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            Button {
                coordinator.portfolioViewModel.onAssetSearch()
                syncRoute()
            } label: {
                Image(systemName: "plus")
                    .font(.title2)
                    .frame(width: 56, height: 56)
            }
            .buttonStyle(.borderedProminent)
            .clipShape(Circle())
            .accessibilityLabel("Добавить актив")
            .padding(24)
        }
    }

    private var assetSearchContent: some View {
        VStack(alignment: .leading) {
            Button {
                coordinator.assetSearchViewModel.onBack()
                syncRoute()
            } label: {
                Label("Назад", systemImage: "chevron.left")
            }

            Text(assetSearchState.message)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .padding(16)
    }

    private var portfolioState: PortfolioState {
        coordinator.portfolioViewModel.state.value as! PortfolioState
    }

    private var assetSearchState: AssetSearchState {
        coordinator.assetSearchViewModel.state.value as! AssetSearchState
    }

    private func syncRoute() {
        currentRoute = (coordinator.state.value as? PortfolioNavigationState)?.currentRoute
            ?? .portfolio
    }
}
