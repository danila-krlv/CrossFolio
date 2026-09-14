package com.crossfolio.common.navigation

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.network.ApiKeyValidationStatus
import com.crossfolio.common.core.network.ApiKeyValidator
import com.crossfolio.common.core.network.NetworkFailure
import com.crossfolio.common.core.network.NetworkProtocol
import com.crossfolio.common.core.network.NetworkResult
import com.crossfolio.common.portfolio.PortfolioCoordinator
import com.crossfolio.common.portfolio.PortfolioRoute
import com.crossfolio.common.profile.ProfileViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiKeyValidationTest {
    @Test
    fun missingKeyShowsRootAlertAndNeverLoadsCatalog() {
        val network = FakeNetwork()
        val tabs = tabs(network, ProfileViewModel())
        assertEquals(ApiKeyValidationStatus.MISSING, tabs.state.value.apiKeyValidation.status)
        assertEquals("Добавьте действительный API-ключ CoinMarketCap в профиле.", tabs.state.value.alertMessage)
        tabs.dismissAlert()
        tabs.portfolioCoordinator.portfolioViewModel.onAssetSearch()
        assertNull(tabs.state.value.alertMessage)
        assertFalse(tabs.portfolioCoordinator.state.value.isSearchEnabled)
        assertEquals(PortfolioRoute.PORTFOLIO, tabs.portfolioCoordinator.state.value.currentRoute)
        assertEquals(0, network.validations.size)
        assertEquals(0, network.catalogs.size)
    }

    @Test
    fun validatesOnlyAtStartupAndOnSavedKeyChanges() {
        val network = FakeNetwork()
        val profile = profile()
        val tabs = tabs(network, profile)
        val portfolio = tabs.portfolioCoordinator
        assertEquals(ApiKeyValidationStatus.CHECKING, tabs.state.value.apiKeyValidation.status)
        assertFalse(portfolio.state.value.isSearchEnabled)
        portfolio.openAssetSearch()
        assertEquals(0, network.catalogs.size)
        network.validations[0](NetworkResult(true, null))
        assertTrue(portfolio.state.value.isSearchEnabled)
        assertEquals(0, network.catalogs.size)
        portfolio.openAssetSearch()
        network.catalogs[0](NetworkResult(listOf(Asset("1", "BTC", name = "Bitcoin")), null))
        portfolio.assetSearchViewModel.search("btc")
        tabs.selectTab(AppTab.PROFILE)
        tabs.selectTab(AppTab.PORTFOLIO)
        portfolio.openAssetSearch()
        assertEquals(1, network.validations.size)
        assertEquals(1, network.catalogs.size)
        assertEquals("btc", portfolio.assetSearchViewModel.state.value.searchText)
        profile.setCoinMarketCapApiKey("first-placeholder")
        profile.setUserName("Danila")
        assertEquals(1, network.validations.size)
        tabs.selectTab(AppTab.PROFILE)
        profile.setCoinMarketCapApiKey("second-placeholder")
        assertEquals(AppTab.PROFILE, tabs.state.value.selectedTab)
        assertEquals(PortfolioRoute.PORTFOLIO, portfolio.state.value.currentRoute)
        assertFalse(portfolio.state.value.isSearchEnabled)
        assertTrue(portfolio.assetSearchViewModel.state.value.assets.isEmpty())
        assertEquals(2, network.validations.size)
        network.validations[1](NetworkResult(true, null))
        portfolio.openAssetSearch()
        assertEquals(2, network.catalogs.size)
    }

    @Test
    fun changingOrRemovingKeyIgnoresOldValidationAndCatalogResponses() {
        val network = FakeNetwork()
        val profile = profile()
        val tabs = tabs(network, profile)
        profile.setCoinMarketCapApiKey("second-placeholder")
        network.validations[0](NetworkResult(true, null))
        assertEquals(ApiKeyValidationStatus.CHECKING, tabs.state.value.apiKeyValidation.status)
        network.validations[1](NetworkResult(true, null))
        tabs.portfolioCoordinator.openAssetSearch()
        profile.setCoinMarketCapApiKey("third-placeholder")
        network.catalogs[0](NetworkResult(null, "Old error", NetworkFailure.INVALID_KEY))
        assertEquals(ApiKeyValidationStatus.CHECKING, tabs.state.value.apiKeyValidation.status)
        assertNull(tabs.state.value.alertMessage)
        profile.setCoinMarketCapApiKey("")
        network.validations[2](NetworkResult(true, null))
        assertEquals(ApiKeyValidationStatus.MISSING, tabs.state.value.apiKeyValidation.status)
        assertFalse(tabs.portfolioCoordinator.state.value.isSearchEnabled)
        assertEquals(3, network.validations.size)
    }

    @Test
    fun invalidKeyAndNetworkFailureHaveDifferentRootAlerts() {
        val network = FakeNetwork()
        val profile = profile()
        val tabs = tabs(network, profile)
        network.validations[0](NetworkResult(null, "Invalid key", NetworkFailure.INVALID_KEY))
        assertEquals(ApiKeyValidationStatus.INVALID, tabs.state.value.apiKeyValidation.status)
        val invalidMessage = tabs.state.value.alertMessage
        assertTrue(invalidMessage != null)
        profile.setCoinMarketCapApiKey("second-placeholder")
        assertNull(tabs.state.value.alertMessage)
        network.validations[1](NetworkResult(null, "Network request failed", NetworkFailure.TRANSPORT))
        assertEquals(ApiKeyValidationStatus.CHECK_FAILED, tabs.state.value.apiKeyValidation.status)
        assertEquals("Не удалось проверить API-ключ. Проверьте подключение к интернету.", tabs.state.value.alertMessage)
        assertFalse(tabs.portfolioCoordinator.state.value.isSearchEnabled)
        tabs.dismissAlert()
        tabs.selectTab(AppTab.PROFILE)
        tabs.selectTab(AppTab.PORTFOLIO)
        tabs.portfolioCoordinator.openAssetSearch()
        assertEquals(2, network.validations.size)
        assertNull(tabs.state.value.alertMessage)
    }

    @Test
    fun catalogNetworkFailureKeepsValidKeyButAuthFailureDisablesSearch() {
        val network = FakeNetwork()
        val tabs = tabs(network, profile())
        network.validations[0](NetworkResult(true, null))
        tabs.portfolioCoordinator.openAssetSearch()
        network.catalogs[0](NetworkResult(null, "Network request failed", NetworkFailure.TRANSPORT))
        assertEquals(ApiKeyValidationStatus.VALID, tabs.state.value.apiKeyValidation.status)
        assertTrue(tabs.portfolioCoordinator.state.value.isSearchEnabled)
        assertEquals("Не удалось загрузить каталог. Проверьте подключение к интернету.", tabs.state.value.alertMessage)
        tabs.dismissAlert()
        tabs.portfolioCoordinator.openAssetSearch()
        network.catalogs[1](NetworkResult(null, "Invalid key", NetworkFailure.INVALID_KEY))
        assertEquals(ApiKeyValidationStatus.INVALID, tabs.state.value.apiKeyValidation.status)
        assertFalse(tabs.portfolioCoordinator.state.value.isSearchEnabled)
        assertEquals(PortfolioRoute.PORTFOLIO, tabs.portfolioCoordinator.state.value.currentRoute)
        assertEquals(1, network.validations.size)
    }

    @Test
    fun validatorClassifiesOtherFailuresWithoutRetainingSecrets() {
        var completion: ((NetworkResult<Boolean>) -> Unit)? = null
        val validator = ApiKeyValidator { completion = it }
        assertEquals(ApiKeyValidationStatus.UNCHECKED, validator.state.value.status)
        for (failure in listOf(NetworkFailure.HTTP, NetworkFailure.API, NetworkFailure.INVALID_RESPONSE)) {
            validator.check(true)
            completion!!(NetworkResult(null, "private-placeholder", failure))
            assertEquals(ApiKeyValidationStatus.CHECK_FAILED, validator.state.value.status)
            assertEquals(failure, validator.state.value.failure)
            assertFalse(validator.state.value.toString().contains("private-placeholder"))
        }
        validator.check(true)
        completion!!(NetworkResult(null, "Stale", NetworkFailure.STALE_RESPONSE))
        assertEquals(ApiKeyValidationStatus.UNCHECKED, validator.state.value.status)
    }
}

private fun profile() = ProfileViewModel().apply { setCoinMarketCapApiKey("first-placeholder") }

private fun tabs(network: FakeNetwork, profile: ProfileViewModel) = TabBarCoordinator(
    portfolioCoordinator = PortfolioCoordinator(network),
    profileViewModel = profile,
    apiKeyValidator = ApiKeyValidator(network::validateApiKey),
)

private class FakeNetwork : NetworkProtocol {
    val validations = mutableListOf<(NetworkResult<Boolean>) -> Unit>()
    val catalogs = mutableListOf<(NetworkResult<List<Asset>>) -> Unit>()
    override fun validateApiKey(completion: (NetworkResult<Boolean>) -> Unit) { validations += completion }
    override fun fetchMap(completion: (NetworkResult<List<Asset>>) -> Unit) { catalogs += completion }
    override fun fetchLogoURL(id: String, completion: (NetworkResult<String>) -> Unit) = error("Unexpected request")
    override fun fetchLogoUrlArray(idString: String, idArray: List<String>,
        completion: (NetworkResult<Map<String, String>>) -> Unit) = error("Unexpected request")
    override fun fetchImg(url: String, completion: (NetworkResult<ByteArray>) -> Unit) = error("Unexpected request")
    override fun fetchPriceArray(idString: String, idArray: List<String>,
        completion: (NetworkResult<Map<String, Double>>) -> Unit) = error("Unexpected request")
}
