import Security
import XCTest
@testable import CrossFolio_iOS

final class ProfileStorageTests: XCTestCase {
    func testPreferencesStoragePersistsUserNameAndTheme() {
        let suiteName = "profile.preferences.test.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let storage = AppleProfilePreferencesStorage(defaults: defaults)
        storage.userName = "Danila"
        storage.theme = .dark

        let restoredStorage = AppleProfilePreferencesStorage(defaults: defaults)
        XCTAssertEqual(restoredStorage.userName, "Danila")
        XCTAssertEqual(restoredStorage.theme, .dark)
    }

    func testSecureStoragePersistsApiKeyInKeychain() {
        let suiteName = "profile.secure.preferences.test.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let service = "profile.secure.test.\(UUID().uuidString)"
        let account = "coinmarketcap_api_key"
        let storage = AppleProfileSecureStorage(service: service, account: account, defaults: defaults)
        defer { storage.coinMarketCapApiKey = "" }

        storage.coinMarketCapApiKey = "test-api-key"

        XCTAssertEqual(
            AppleProfileSecureStorage(service: service, account: account, defaults: defaults).coinMarketCapApiKey,
            "test-api-key"
        )
    }

    func testFreshInstallationClearsSurvivingKeychainApiKey() {
        let suiteName = "profile.reinstall.test.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        let service = "profile.reinstall.secure.test.\(UUID().uuidString)"
        let storage = AppleProfileSecureStorage(service: service, defaults: defaults)
        defer {
            storage.coinMarketCapApiKey = ""
            defaults.removePersistentDomain(forName: suiteName)
        }
        storage.coinMarketCapApiKey = "test-api-key"
        XCTAssertEqual(storage.coinMarketCapApiKey, "test-api-key")

        // Simulate uninstall: the app preferences disappear, but Keychain remains.
        defaults.removePersistentDomain(forName: suiteName)
        let reinstalledStorage = AppleProfileSecureStorage(service: service, defaults: defaults)
        XCTAssertEqual(reinstalledStorage.coinMarketCapApiKey, "")

        reinstalledStorage.coinMarketCapApiKey = "new-test-api-key"
        XCTAssertEqual(
            AppleProfileSecureStorage(service: service, defaults: defaults).coinMarketCapApiKey,
            "new-test-api-key"
        )
    }
}
