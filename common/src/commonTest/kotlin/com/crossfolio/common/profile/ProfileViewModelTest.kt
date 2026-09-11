package com.crossfolio.common.profile

import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileViewModelTest {
    @Test
    fun readsAndWritesProfileFieldsThroughPlatformStorages() {
        val preferencesStorage = FakePreferencesStorage(
            userName = "Danila",
            theme = AppTheme.DARK,
        )
        val secureStorage = FakeSecureStorage(coinMarketCapApiKey = "initial-key")
        val viewModel = ProfileViewModel(preferencesStorage, secureStorage)

        assertEquals("Danila", viewModel.state.value.userName)
        assertEquals(AppTheme.DARK, viewModel.state.value.theme)
        assertEquals("initial-key", viewModel.state.value.coinMarketCapApiKey)

        viewModel.setUserName("Daniel")
        viewModel.setTheme(AppTheme.LIGHT)
        viewModel.setCoinMarketCapApiKey("updated-key")

        assertEquals("Daniel", preferencesStorage.userName)
        assertEquals(AppTheme.LIGHT, preferencesStorage.theme)
        assertEquals("updated-key", secureStorage.coinMarketCapApiKey)
        assertEquals(
            ProfileState(
                userName = "Daniel",
                theme = AppTheme.LIGHT,
                coinMarketCapApiKey = "updated-key",
            ),
            viewModel.state.value,
        )
    }
}

private class FakePreferencesStorage(
    override var userName: String,
    override var theme: AppTheme,
) : ProfilePreferencesStorage

private class FakeSecureStorage(
    override var coinMarketCapApiKey: String,
) : ProfileSecureStorage
