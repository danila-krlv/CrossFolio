import Common
import SwiftUI

struct ProfileScreen: View {
    let viewModel: ProfileViewModel
    @FocusState private var isApiKeyFocused: Bool
    @State private var userName: String
    @State private var theme: AppTheme
    @State private var coinMarketCapApiKey: String
    @State private var stopObservingKey: (() -> Void)?

    init(viewModel: ProfileViewModel) {
        self.viewModel = viewModel

        let state = viewModel.state.value as! ProfileState
        _userName = State(initialValue: state.userName)
        _theme = State(initialValue: state.theme)
        _coinMarketCapApiKey = State(initialValue: viewModel.getCoinMarketCapApiKey())
    }

    var body: some View {
        Form {
            TextField("Имя пользователя", text: userNameBinding)

            Picker("Тема", selection: themeBinding) {
                Text("Системная").tag(AppTheme.system)
                Text("Светлая").tag(AppTheme.light)
                Text("Тёмная").tag(AppTheme.dark)
            }
            .pickerStyle(.segmented)

            SecureField("CoinMarketCap API-ключ", text: apiKeyBinding)
                .focused($isApiKeyFocused)
                .onSubmit {
                    viewModel.finishApiKeyEditing()
                    isApiKeyFocused = false
                }
                .onChange(of: isApiKeyFocused) { _, focused in
                    if focused { viewModel.beginApiKeyEditing() }
                    else { viewModel.finishApiKeyEditing() }
                }
        }
        .onAppear {
            stopObservingKey?()
            stopObservingKey = viewModel.apiKeyManager.observeState { state in
                if !state.isEditing && (state.status == .valid || state.status == .missing) {
                    coinMarketCapApiKey = viewModel.getCoinMarketCapApiKey()
                }
            }
        }
        .onDisappear {
            viewModel.finishApiKeyEditing()
            stopObservingKey?()
            stopObservingKey = nil
        }
    }

    private var userNameBinding: Binding<String> {
        Binding(
            get: { userName },
            set: {
                userName = $0
                viewModel.setUserName(userName: $0)
            }
        )
    }

    private var themeBinding: Binding<AppTheme> {
        Binding(
            get: { theme },
            set: {
                theme = $0
                viewModel.setTheme(theme: $0)
            }
        )
    }

    private var apiKeyBinding: Binding<String> {
        Binding(
            get: { coinMarketCapApiKey },
            set: {
                coinMarketCapApiKey = $0
                viewModel.editCoinMarketCapApiKey(apiKey: $0)
            }
        )
    }
}
