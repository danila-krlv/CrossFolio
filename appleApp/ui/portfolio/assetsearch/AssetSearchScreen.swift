import Common
import SwiftUI
#if os(iOS)
import UIKit
#else
import AppKit
#endif

struct AssetSearchScreen: View {
    let viewModel: AssetSearchViewModel
    @State private var state: AssetSearchState
    @State private var stopObserving: (() -> Void)?
    #if os(macOS)
    @State private var selectedAssetID: String?
    private enum Focus { case search, results }
    @FocusState private var focus: Focus?
    #endif

    init(viewModel: AssetSearchViewModel) {
        self.viewModel = viewModel
        _state = State(initialValue: viewModel.state.value as! AssetSearchState)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            TextField("Тикер или название", text: searchText)
                .textFieldStyle(.roundedBorder)
                .autocorrectionDisabled()
                #if os(macOS)
                .focused($focus, equals: .search)
                .onSubmit { selectHighlightedAsset() }
                .onKeyPress(.downArrow) {
                    if selectedAssetID == nil { selectedAssetID = state.assets.first?.searchId }
                    focus = .results
                    return .handled
                }
                #endif
                #if os(iOS)
                .textInputAutocapitalization(.never)
                #endif

            if state.isLoading {
                ProgressView("Загрузка каталога…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if state.error != nil {
                Text("Не удалось загрузить каталог.")
                Button("Повторить") { viewModel.loadCatalog(forceRefresh: false) }
                Spacer()
            } else if state.assets.isEmpty {
                Text("Монеты не найдены")
                    .foregroundStyle(.secondary)
                Spacer()
            } else {
                #if os(macOS)
                List(state.assets, id: \.searchId, selection: $selectedAssetID) { asset in
                    AssetSearchRow(asset: asset, logoURL: state.logoUrls[asset.searchId])
                        .contentShape(Rectangle())
                        .tag(asset.searchId)
                        .onTapGesture(count: 2) { viewModel.selectAsset(asset: asset) }
                        .onAppear { viewModel.loadLogo(asset: asset) }
                }
                .focused($focus, equals: .results)
                .onKeyPress(.return) {
                    selectHighlightedAsset()
                    return .handled
                }
                .listStyle(.inset)
                HStack {
                    Text("Выберите актив и нажмите Enter или дважды нажмите на строку")
                        .font(.caption).foregroundStyle(.secondary)
                    Spacer()
                    Button("Продолжить") { selectHighlightedAsset() }
                        .disabled(selectedAssetID == nil)
                }
                #else
                List(state.assets, id: \.searchId) { asset in
                    Button {
                        viewModel.selectAsset(asset: asset)
                    } label: {
                        AssetSearchRow(asset: asset, logoURL: state.logoUrls[asset.searchId])
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .onAppear { viewModel.loadLogo(asset: asset) }
                }
                .listStyle(.plain)
                #endif
            }
        }
        #if os(macOS)
        .onChange(of: state.searchText) { _, _ in selectedAssetID = nil }
        .toolbar {
            Button { focus = .search } label: {
                Label("Поиск", systemImage: "magnifyingglass")
            }
            .keyboardShortcut("f", modifiers: .command)
        }
        #endif
        .onAppear {
            stopObserving?()
            stopObserving = viewModel.observeState { state = $0 }
            #if os(macOS)
            focus = .search
            #endif
        }
        .onDisappear {
            stopObserving?()
            stopObserving = nil
        }
    }

    #if os(macOS)
    private func selectHighlightedAsset() {
        guard let asset = state.assets.first(where: { $0.searchId == selectedAssetID }) else { return }
        viewModel.selectAsset(asset: asset)
    }
    #endif

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
            AssetLogo(url: logoURL)

            VStack(alignment: .leading, spacing: 4) {
                Text(asset.ticker).font(.headline)
                Text(asset.name).font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
    }
}

struct AssetLogo: View {
    let url: String?
    @State private var image: Image?
    @State private var isLoading = false

    var body: some View {
        ZStack {
            if let image {
                image.resizable().scaledToFit()
            } else if isLoading {
                ProgressView()
            } else {
                Image(systemName: "photo").foregroundStyle(.secondary)
            }
        }
        .frame(width: 44, height: 44)
        .accessibilityHidden(true)
        .task(id: url) { await load() }
    }

    @MainActor
    private func load() async {
        image = nil
        isLoading = false
        guard let url, let address = URL(string: url), address.scheme == "https" else { return }
        isLoading = true
        defer { if !Task.isCancelled { isLoading = false } }
        do {
            // Public CDN request: no profile key or authenticated headers.
            let (data, response) = try await URLSession.shared.data(from: address)
            guard !Task.isCancelled, let response = response as? HTTPURLResponse,
                  (200..<300).contains(response.statusCode) else { return }
            #if os(iOS)
            if let decoded = UIImage(data: data) { image = Image(uiImage: decoded) }
            #else
            if let decoded = NSImage(data: data) { image = Image(nsImage: decoded) }
            #endif
        } catch {
            // Keep a placeholder after a failure; appearing again starts a fresh attempt.
        }
    }
}
