import Common
import SwiftUI

struct ProfileScreen: View {
    let viewModel: ProfileViewModel
    @State private var userName: String
    @State private var theme: AppTheme
    @State private var coinMarketCapApiKey: String

    init(viewModel: ProfileViewModel) {
        self.viewModel = viewModel

        let state = viewModel.state.value as! ProfileState
        _userName = State(initialValue: state.userName)
        _theme = State(initialValue: state.theme)
        _coinMarketCapApiKey = State(initialValue: state.coinMarketCapApiKey)
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
                viewModel.setCoinMarketCapApiKey(apiKey: $0)
            }
        )
    }
}
