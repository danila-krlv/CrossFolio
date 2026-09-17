import Common
import SwiftUI

struct PortfolioScreen: View {
    private let coordinator: PortfolioCoordinator
    @State private var isSearchEnabled: Bool
    @State private var currentRoute: PortfolioRoute
    @State private var portfolioState: PortfolioState
    @State private var stopObserving: (() -> Void)?
    @State private var stopObservingPortfolio: (() -> Void)?

    init(coordinator: PortfolioCoordinator) {
        self.coordinator = coordinator
        _isSearchEnabled = State(initialValue:
            (coordinator.state.value as? PortfolioNavigationState)?.isSearchEnabled ?? false)
        _currentRoute = State(
            initialValue: (coordinator.state.value as? PortfolioNavigationState)?.currentRoute
                ?? .portfolio
        )
        _portfolioState = State(
            initialValue: coordinator.portfolioViewModel.state.value as! PortfolioState
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
            stopObservingPortfolio?()
            stopObservingPortfolio = coordinator.portfolioViewModel.observeState {
                portfolioState = $0
            }
        }
        .onDisappear {
            stopObserving?()
            stopObserving = nil
            stopObservingPortfolio?()
            stopObservingPortfolio = nil
        }
    }

    private var portfolioContent: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Стоимость портфеля")
                        .font(.headline)
                        .foregroundStyle(.secondary)
                    Text("$\(portfolioState.totalValueUsdText)")
                        .font(.largeTitle.weight(.semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 20)

                Divider()

                if portfolioState.isLoading && portfolioState.positions.isEmpty {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if portfolioState.storageFailure != nil && portfolioState.positions.isEmpty {
                    VStack(spacing: 12) {
                        Text("Не удалось загрузить портфель")
                        Button("Повторить") { coordinator.portfolioViewModel.loadPositions() }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if portfolioState.rows.isEmpty {
                    Text("Добавьте первый актив")
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List(portfolioState.rows, id: \.position.asset.searchId) { row in
                        PortfolioRow(
                            row: row,
                            logoURL: coordinator.portfolioViewModel.logoUrl(asset: row.position.asset)
                        )
                    }
                    .listStyle(.plain)
                }
            }

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

    private func syncRoute() {
        currentRoute = (coordinator.state.value as? PortfolioNavigationState)?.currentRoute
            ?? .portfolio
    }
}

private struct PortfolioRow: View {
    let row: PortfolioRowState
    let logoURL: String?

    var body: some View {
        HStack(spacing: 12) {
            AssetLogo(url: logoURL)
            VStack(alignment: .leading, spacing: 4) {
                Text(row.position.asset.ticker).font(.headline)
                Text("\(row.position.quantity.value) \(row.position.asset.ticker)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 8)
            Text("$\(row.valueUsdText)")
                .font(.headline)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .padding(.vertical, 4)
    }
}
