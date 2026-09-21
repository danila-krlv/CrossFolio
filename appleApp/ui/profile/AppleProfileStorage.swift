import Common
import Foundation
import Security

final class AppleProfilePreferencesStorage: ProfilePreferencesStorage {
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var userName: String {
        get { defaults.string(forKey: Keys.userName) ?? "" }
        set { defaults.set(newValue, forKey: Keys.userName) }
    }

    var theme: AppTheme {
        get {
            switch defaults.string(forKey: Keys.theme) {
            case "light": .light
            case "dark": .dark
            default: .system
            }
        }
        set {
            let value = switch newValue {
            case .light: "light"
            case .dark: "dark"
            default: "system"
            }
            defaults.set(value, forKey: Keys.theme)
        }
    }

    private enum Keys {
        static let userName = "profile.userName"
        static let theme = "profile.theme"
    }
}

final class AppleProfileSecureStorage: ProfileSecureStorage {
    private let service: String
    private let account: String
    private let defaults: UserDefaults
    private let installationMarker = "profile.secureStorageInitialized"

    init(
        service: String = Bundle.main.bundleIdentifier ?? "com.crossfolio",
        account: String = "coinmarketcap_api_key",
        defaults: UserDefaults = .standard
    ) {
        self.service = service
        self.account = account
        self.defaults = defaults

        // Keychain can survive uninstall; UserDefaults belongs to this installation.
        if !defaults.bool(forKey: installationMarker) {
            let status = SecItemDelete(baseQuery as CFDictionary)
            if status == errSecSuccess || status == errSecItemNotFound {
                defaults.set(true, forKey: installationMarker)
            }
        }
    }

    var coinMarketCapApiKey: String {
        get {
            guard defaults.bool(forKey: installationMarker) else { return "" }
            var query = baseQuery
            query[kSecReturnData as String] = true
            query[kSecMatchLimit as String] = kSecMatchLimitOne

            var result: CFTypeRef?
            guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
                  let data = result as? Data
            else {
                return ""
            }
            return String(data: data, encoding: .utf8) ?? ""
        }
        set {
            guard !newValue.isEmpty else {
                SecItemDelete(baseQuery as CFDictionary)
                return
            }

            let data = Data(newValue.utf8)
            let status = SecItemUpdate(
                baseQuery as CFDictionary,
                [kSecValueData as String: data] as CFDictionary
            )
            if status == errSecItemNotFound {
                var query = baseQuery
                query[kSecValueData as String] = data
                SecItemAdd(query as CFDictionary, nil)
            }
        }
    }

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}
