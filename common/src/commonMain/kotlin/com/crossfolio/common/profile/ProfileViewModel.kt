package com.crossfolio.common.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProfileState(
    val message: String = "hello ProfileViewModel",
    val userName: String = "",
    val theme: AppTheme = AppTheme.SYSTEM,
    val coinMarketCapApiKey: String = "",
)

class ProfileViewModel(
    private val preferencesStorage: ProfilePreferencesStorage = InMemoryProfilePreferencesStorage(),
    private val secureStorage: ProfileSecureStorage = InMemoryProfileSecureStorage(),
) {
    private val _state = MutableStateFlow(
        ProfileState(
            userName = preferencesStorage.userName,
            theme = preferencesStorage.theme,
            coinMarketCapApiKey = secureStorage.coinMarketCapApiKey,
        ),
    )
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    fun setUserName(userName: String) {
        preferencesStorage.userName = userName
        _state.value = _state.value.copy(userName = userName)
    }

    fun setTheme(theme: AppTheme) {
        preferencesStorage.theme = theme
        _state.value = _state.value.copy(theme = theme)
    }

    fun setCoinMarketCapApiKey(apiKey: String) {
        secureStorage.coinMarketCapApiKey = apiKey
        _state.value = _state.value.copy(coinMarketCapApiKey = apiKey)
    }
}
