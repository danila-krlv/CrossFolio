package com.crossfolio.common.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProfileState(
    val userName: String = "",
    val theme: AppTheme = AppTheme.SYSTEM,
    val hasApiKey: Boolean = false,
)

class ProfileViewModel(
    private val preferencesStorage: ProfilePreferencesStorage?,
    private val secureStorage: ProfileSecureStorage?,
) {
    constructor() : this(null, null)

    internal var onApiKeyChanged: () -> Unit = {}

    private var inMemoryApiKey = ""

    private val _state = MutableStateFlow(
        ProfileState(
            userName = preferencesStorage?.userName.orEmpty(),
            theme = preferencesStorage?.theme ?: AppTheme.SYSTEM,
            hasApiKey = getCoinMarketCapApiKey().isNotEmpty(),
        ),
    )
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    fun setUserName(userName: String) {
        preferencesStorage?.userName = userName
        _state.value = _state.value.copy(userName = userName)
    }

    fun setTheme(theme: AppTheme) {
        preferencesStorage?.theme = theme
        _state.value = _state.value.copy(theme = theme)
    }

    fun setCoinMarketCapApiKey(apiKey: String) {
        val previousKey = getCoinMarketCapApiKey()
        if (secureStorage != null) {
            secureStorage.coinMarketCapApiKey = apiKey
        } else {
            inMemoryApiKey = apiKey
        }
        val savedKey = getCoinMarketCapApiKey()
        _state.value = _state.value.copy(hasApiKey = savedKey.isNotEmpty())
        if (savedKey != previousKey) onApiKeyChanged()
    }

    fun getCoinMarketCapApiKey(): String = secureStorage?.coinMarketCapApiKey ?: inMemoryApiKey
}
