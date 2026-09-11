package com.crossfolio.common.profile

enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK,
}

interface ProfilePreferencesStorage {
    var userName: String
    var theme: AppTheme
}

interface ProfileSecureStorage {
    var coinMarketCapApiKey: String
}

internal class InMemoryProfilePreferencesStorage : ProfilePreferencesStorage {
    override var userName: String = ""
    override var theme: AppTheme = AppTheme.SYSTEM
}

internal class InMemoryProfileSecureStorage : ProfileSecureStorage {
    override var coinMarketCapApiKey: String = ""
}
