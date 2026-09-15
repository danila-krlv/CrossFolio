import Common
import SwiftUI

struct PortfolioScreen: View {
    private let coordinator: PortfolioCoordinator
    @State private var isSearchEnabled: Bool
    @State private var currentRoute: PortfolioRoute
    @State private var stopObserving: (() -> Void)?

    init(coordinator: PortfolioCoordinator) {
        self.coordinator = coordinator
        _isSearchEnabled = State(initialValue:
            (coordinator.state.value as? PortfolioNavigationState)?.isSearchEnabled ?? false)
        _currentRoute = State(
            initialValue: (coordinator.state.value as? PortfolioNavigationState)?.currentRoute
                ?? .portfolio
        )
    }

    var body: some View {
        Group {
            if currentRoute == .portfolio {
                portfolioContent
            } else if currentRoute == .assetSearch {
                assetSearchContent
            } else if currentRoute == .edit {
                if let viewModel = coordinator.editViewModel {
                    EditScreen(viewModel: viewModel)
                } else {
                    portfolioContent
                }
            }
        }
        .onAppear {
            stopObserving?()
            stopObserving = coordinator.observeState {
                currentRoute = $0.currentRoute
                isSearchEnabled = $0.isSearchEnabled
            }
        }
        .onDisappear {
            stopObserving?()
            stopObserving = nil
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
            .disabled(!isSearchEnabled)
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

            AssetSearchScreen(viewModel: coordinator.assetSearchViewModel)
        }
        .padding(16)
    }

    private var portfolioState: PortfolioState {
        coordinator.portfolioViewModel.state.value as! PortfolioState
    }

    private func syncRoute() {
        currentRoute = (coordinator.state.value as? PortfolioNavigationState)?.currentRoute
            ?? .portfolio
    }
}
