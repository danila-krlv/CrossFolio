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
        let service = "profile.secure.test.\(UUID().uuidString)"
        let account = "coinmarketcap_api_key"
        let storage = AppleProfileSecureStorage(service: service, account: account)
        defer { storage.coinMarketCapApiKey = "" }

        storage.coinMarketCapApiKey = "test-api-key"

        XCTAssertEqual(
            AppleProfileSecureStorage(service: service, account: account).coinMarketCapApiKey,
            "test-api-key"
        )
    }
}
