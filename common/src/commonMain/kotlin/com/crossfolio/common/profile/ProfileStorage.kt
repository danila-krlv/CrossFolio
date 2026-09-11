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
