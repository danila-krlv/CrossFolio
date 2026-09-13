package com.crossfolio.common.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileViewModelTest {
    @Test
    fun doesNotCacheInputWhenSecureStorageRejectsWrite() {
        val storage = object : ProfileSecureStorage {
            override var coinMarketCapApiKey: String
                get() = ""
                set(value) {}
        }
        val viewModel = ProfileViewModel(null, storage)
        viewModel.setCoinMarketCapApiKey("rejected-placeholder")
        assertFalse(viewModel.state.value.hasApiKey)
        assertEquals("", viewModel.getCoinMarketCapApiKey())
        assertFalse(viewModel.state.value.toString().contains("rejected-placeholder"))
    }

    @Test
    fun updatesStateWithoutPlatformStorages() {
        val viewModel = ProfileViewModel()

        viewModel.setUserName("Danila")
        viewModel.setTheme(AppTheme.DARK)
        viewModel.setCoinMarketCapApiKey("api-key")

        assertEquals(
            ProfileState(
                userName = "Danila",
                theme = AppTheme.DARK,
                hasApiKey = true,
            ),
            viewModel.state.value,
        )
        assertEquals("api-key", viewModel.getCoinMarketCapApiKey())
        assertFalse(viewModel.state.value.toString().contains("api-key"))
        viewModel.setCoinMarketCapApiKey("")
        assertFalse(viewModel.state.value.hasApiKey)
    }

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
        assertEquals("initial-key", viewModel.getCoinMarketCapApiKey())
        assertTrue(viewModel.state.value.hasApiKey)

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
                hasApiKey = true,
            ),
            viewModel.state.value,
        )
        assertFalse(viewModel.state.value.toString().contains("updated-key"))
        secureStorage.coinMarketCapApiKey = "changed-in-storage"
        assertEquals("changed-in-storage", viewModel.getCoinMarketCapApiKey())
    }
}

private class FakePreferencesStorage(
    override var userName: String,
    override var theme: AppTheme,
) : ProfilePreferencesStorage

private class FakeSecureStorage(
    override var coinMarketCapApiKey: String,
) : ProfileSecureStorage
