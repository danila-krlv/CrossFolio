import Common
import SwiftUI

struct AssetSearchScreen: View {
    let viewModel: AssetSearchViewModel
    @State private var state: AssetSearchState
    @State private var stopObserving: (() -> Void)?

    init(viewModel: AssetSearchViewModel) {
        self.viewModel = viewModel
        _state = State(initialValue: viewModel.state.value as! AssetSearchState)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            TextField("Тикер или название", text: searchText)
                .textFieldStyle(.roundedBorder)
                .autocorrectionDisabled()
                #if os(iOS)
                .textInputAutocapitalization(.never)
                #endif

            if state.isLoading {
                ProgressView("Загрузка каталога…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if state.error != nil {
                Text("Не удалось загрузить каталог. Проверьте API-ключ в профиле и подключение к интернету.")
                Button("Повторить") { viewModel.loadCatalog() }
                Spacer()
            } else if state.assets.isEmpty {
                Text("Монеты не найдены")
                    .foregroundStyle(.secondary)
                Spacer()
            } else {
                List(state.assets, id: \.searchId) { asset in
                    AssetSearchRow(asset: asset, logoURL: state.logoUrls[asset.searchId])
                        .onAppear { viewModel.loadLogo(id: asset.searchId) }
                }
                .listStyle(.plain)
            }
        }
        .onAppear {
            stopObserving?()
            stopObserving = viewModel.observeState { state = $0 }
        }
        .onDisappear {
            stopObserving?()
            stopObserving = nil
        }
    }

    private var searchText: Binding<String> {
        Binding(
            get: { state.searchText },
            set: {
                viewModel.search(searchText: $0)
                state = viewModel.state.value as! AssetSearchState
            }
        )
    }
}

private struct AssetSearchRow: View {
    let asset: Asset
    let logoURL: String?

    var body: some View {
        HStack(spacing: 12) {
            AsyncImage(url: logoURL.flatMap(URL.init(string:))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFit()
                } else {
                    Image(systemName: "circle.dotted")
                        .foregroundStyle(.secondary)
                }
            }
            .frame(width: 44, height: 44)
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 4) {
                Text(asset.ticker).font(.headline)
                Text(asset.name).font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
    }
}
