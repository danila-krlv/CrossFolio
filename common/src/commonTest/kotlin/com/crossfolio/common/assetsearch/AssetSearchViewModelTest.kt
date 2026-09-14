package com.crossfolio.common.assetsearch

import com.crossfolio.common.asset.Asset
import com.crossfolio.common.navigation.AppTab
import com.crossfolio.common.navigation.TabBarCoordinator
import com.crossfolio.common.portfolio.PortfolioCoordinator
import com.crossfolio.common.portfolio.PortfolioRoute
import com.crossfolio.common.profile.ProfileSecureStorage
import com.crossfolio.common.profile.ProfileViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssetSearchViewModelTest {
    private val catalog = listOf(
        Asset("1027", "ETH", name = "Ethereum", slug = "ethereum", rank = 2),
        Asset("1", "BTC", name = "Bitcoin"),
        Asset("999", "ETH", name = "Other Ethereum"),
    )

    @Test
    fun selectingAssetOpensEditAndBackPreservesSearch() {
        val network = FakeNetwork()
        val coordinator = PortfolioCoordinator(network, { "placeholder-key" })
        network.complete(NetworkResult(catalog, null))
        coordinator.openAssetSearch()
        network.complete(NetworkResult(catalog, null))
        val search = coordinator.assetSearchViewModel
        search.search("ETH")
        val searchState = search.state.value
        val requestCount = network.mapRequests

        search.selectAsset(catalog.first())
        assertEquals(PortfolioRoute.EDIT, coordinator.state.value.currentRoute)
        val edit = requireNotNull(coordinator.editViewModel)
        assertEquals(catalog.first(), edit.asset)
        edit.onBack()

        assertEquals(PortfolioRoute.ASSET_SEARCH, coordinator.state.value.currentRoute)
        assertEquals(searchState, search.state.value)
        assertEquals(requestCount, network.mapRequests)
        assertNull(coordinator.editViewModel)
    }

    @Test
    fun missingKeyShowsSameAlertAtStartupAndOnSearchAttempt() {
        val network = FakeNetwork()
        val coordinator = PortfolioCoordinator(network, { "" })
        val message = coordinator.state.value.alertMessage
        assertEquals("Добавьте действительный API-ключ CoinMarketCap в профиле.", message)
        assertEquals(0, network.mapRequests)
        coordinator.dismissAlert()
        assertNull(coordinator.state.value.alertMessage)
        coordinator.portfolioViewModel.onAssetSearch()
        assertEquals(message, coordinator.state.value.alertMessage)
        assertEquals(PortfolioRoute.PORTFOLIO, coordinator.state.value.currentRoute)
        assertEquals(0, network.mapRequests)
    }

    @Test
    fun invalidKeyNeverOpensSearchAndCorrectedKeyOpensOnlyAfterValidation() {
        var key = "invalid-placeholder"
        val network = FakeNetwork { key }
        val coordinator = PortfolioCoordinator(network, { key })
        network.complete(NetworkResult(null, "CoinMarketCap error 1001: API request rejected"))
        val message = coordinator.state.value.alertMessage
        assertTrue(message != null)
        coordinator.dismissAlert()
        coordinator.portfolioViewModel.onAssetSearch()
        assertEquals(PortfolioRoute.PORTFOLIO, coordinator.state.value.currentRoute)
        assertNull(coordinator.state.value.alertMessage)
        network.complete(NetworkResult(null, "CoinMarketCap error 1001: API request rejected"))
        assertEquals(message, coordinator.state.value.alertMessage)
        assertEquals(PortfolioRoute.PORTFOLIO, coordinator.state.value.currentRoute)
        key = "valid-placeholder"
        coordinator.portfolioViewModel.onAssetSearch()
        assertEquals(PortfolioRoute.PORTFOLIO, coordinator.state.value.currentRoute)
        network.complete(NetworkResult(catalog, null))
        assertEquals(PortfolioRoute.ASSET_SEARCH, coordinator.state.value.currentRoute)
        assertNull(coordinator.state.value.alertMessage)
    }

    @Test
    fun networkFailureBlocksSearchWithoutCallingKeyInvalid() {
        val network = FakeNetwork()
        val coordinator = PortfolioCoordinator(network, { "placeholder-key" })
        network.complete(NetworkResult(catalog, null))
        coordinator.openAssetSearch()
        network.complete(NetworkResult(null, "Network request failed"))
        assertEquals(PortfolioRoute.PORTFOLIO, coordinator.state.value.currentRoute)
        assertFalse(coordinator.assetSearchViewModel.state.value.isApiKeyInvalid)
        assertEquals("Не удалось проверить API-ключ. Проверьте подключение к интернету и повторите попытку.",
            coordinator.state.value.alertMessage)
    }

    @Test
    fun doesNotRequestCatalogBeforeOpeningAndRejectsEmptyOrMalformedKey() {
        val network = FakeNetwork()
        var key = ""
        val model = AssetSearchViewModel({}, network, { key })
        assertEquals(0, network.mapRequests)
        for (value in listOf("", "   ", "placeholder\nkey")) {
            key = value
            var valid: Boolean? = null
            model.openSearch { valid = it }
            assertEquals(false, valid)
            assertTrue(model.state.value.isApiKeyInvalid)
            assertFalse(model.state.value.error.orEmpty().contains("placeholder"))
        }
        assertEquals(0, network.mapRequests)
    }

    @Test
    fun rejectsInvalidKeyButAllowsRetryAfterNetworkFailure() {
        val network = FakeNetwork()
        val model = AssetSearchViewModel({}, network, { "placeholder-key" })
        model.openSearch()
        network.complete(NetworkResult(null, "CoinMarketCap error 1001: API request rejected"))
        assertTrue(model.state.value.isApiKeyInvalid)
        model.openSearch()
        network.complete(NetworkResult(null, "Network request failed"))
        assertFalse(model.state.value.isLoading)
        assertEquals("Network request failed", model.state.value.error)
    }

    @Test
    fun revalidatesOnOpeningAndIgnoresResponseForChangedKey() {
        var key = "first-placeholder"
        val network = FakeNetwork { key }
        val model = AssetSearchViewModel({}, network, { key })
        model.openSearch()
        network.complete(NetworkResult(catalog, null))
        key = "second-placeholder"
        model.openSearch()
        assertTrue(model.state.value.assets.isEmpty())
        assertTrue(model.state.value.isLoading)
        key = "third-placeholder"
        network.complete(NetworkResult(null, "CoinMarketCap error 1001: API request rejected"))
        assertFalse(model.state.value.isLoading)
        model.openSearch()
        network.complete(NetworkResult(catalog, null))
        assertEquals(catalog, model.state.value.assets)
        assertEquals(listOf("first-placeholder", "second-placeholder", "third-placeholder"), network.keys)
    }

    @Test
    fun ignoresSupersededRequestWhileNewValidationIsLoading() {
        val network = FakeNetwork()
        val model = AssetSearchViewModel({}, network, { "placeholder-key" })
        model.openSearch()
        model.openSearch()
        network.complete(NetworkResult(null, "CoinMarketCap error 1001: API request rejected"))
        assertTrue(model.state.value.isLoading)
        network.complete(NetworkResult(catalog, null))
        assertEquals(catalog, model.state.value.assets)
    }

    @Test
    fun changingProfileKeyResetsNavigationAndNextSearchRevalidates() {
        val profile = ProfileViewModel()
        profile.setCoinMarketCapApiKey("first-placeholder")
        val network = FakeNetwork(profile::getCoinMarketCapApiKey)
        val portfolio = PortfolioCoordinator(network, profile::getCoinMarketCapApiKey)
        network.complete(NetworkResult(catalog, null))
        val tabs = TabBarCoordinator(portfolioCoordinator = portfolio, profileViewModel = profile)
        portfolio.portfolioViewModel.onAssetSearch()
        network.complete(NetworkResult(catalog, null))
        tabs.selectTab(AppTab.PROFILE)
        profile.setCoinMarketCapApiKey("second-placeholder")
        assertEquals(AppTab.PORTFOLIO, tabs.state.value.selectedTab)
        assertEquals(PortfolioRoute.PORTFOLIO, portfolio.state.value.currentRoute)
        assertNull(portfolio.state.value.alertMessage)
        assertEquals(2, network.mapRequests)
        portfolio.openAssetSearch()
        assertTrue(portfolio.assetSearchViewModel.state.value.assets.isEmpty())
        assertEquals(listOf("first-placeholder", "first-placeholder", "second-placeholder"), network.keys)
        network.complete(NetworkResult(catalog, null))
    }

    @Test
    fun navigationResetIgnoresPendingValidation() {
        val network = FakeNetwork()
        val portfolio = PortfolioCoordinator(network, { "placeholder-key" })
        network.complete(NetworkResult(catalog, null))
        portfolio.openAssetSearch()
        portfolio.resetNavigation()
        network.complete(NetworkResult(catalog, null))
        assertEquals(PortfolioRoute.PORTFOLIO, portfolio.state.value.currentRoute)
        assertNull(portfolio.state.value.alertMessage)
    }

    @Test
    fun loadsOnceAndSearchesLocallyByCaseInsensitivePrefix() {
        val network = FakeNetwork()
        val model = AssetSearchViewModel({}, network)
        model.loadCatalog()
        assertTrue(model.state.value.isLoading)
        model.loadCatalog()
        assertEquals(1, network.mapRequests)
        network.complete(NetworkResult(catalog, null))

        for (text in listOf("eth", "ETH", "EtH")) {
            assertEquals(listOf(catalog[0], catalog[2]), model.search(text))
        }
        assertEquals(listOf(catalog[0]), model.search("ethereum"))
        assertEquals(listOf(catalog[1]), model.search("Bit"))
        assertTrue(model.search("thereum").isEmpty())
        assertEquals(catalog, model.search(""))
        assertEquals(catalog, model.state.value.assets)
        assertFalse(model.state.value.isLoading)
        assertNull(model.state.value.error)
        model.loadCatalog()
        assertEquals(1, network.mapRequests)
    }

    @Test
    fun appliesLatestQueryWhenCatalogArrivesAndCopiesCatalog() {
        val network = FakeNetwork()
        val model = AssetSearchViewModel({}, network)
        model.loadCatalog()
        model.search("btc")
        model.search("eth")
        val response = catalog.toMutableList()
        network.complete(NetworkResult(response, null))
        response.clear()
        assertEquals("eth", model.state.value.searchText)
        assertEquals(listOf(catalog[0], catalog[2]), model.state.value.assets)
        assertEquals(catalog, model.search(""))
    }

    @Test
    fun retriesFailureExplicitlyWithoutRequestsOnEachKeystroke() {
        val network = FakeNetwork()
        val model = AssetSearchViewModel({}, network)
        model.loadCatalog()
        network.complete(NetworkResult(null, "HTTP 503: Request failed"))
        assertFalse(model.state.value.isLoading)
        assertEquals("HTTP 503: Request failed", model.state.value.error)
        model.search("e")
        model.search("eth")
        assertEquals(1, network.mapRequests)
        model.loadCatalog()
        assertTrue(model.state.value.isLoading)
        assertNull(model.state.value.error)
        network.complete(NetworkResult(catalog, null))
        assertEquals(listOf(catalog[0], catalog[2]), model.state.value.assets)
        assertEquals(2, network.mapRequests)
    }

    @Test
    fun treatsEmptyCatalogAsSuccessfulCachedResponse() {
        val network = FakeNetwork()
        val model = AssetSearchViewModel({}, network)
        model.loadCatalog()
        network.complete(NetworkResult(emptyList(), null))
        model.loadCatalog()
        assertEquals(1, network.mapRequests)
        assertFalse(model.state.value.isLoading)
        assertNull(model.state.value.error)
        assertTrue(model.search("").isEmpty())
    }

    @Test
    fun reopeningSearchReadsUpdatedProfileKeyAndBackReturnsToPortfolio() {
        val storage = object : ProfileSecureStorage {
            override var coinMarketCapApiKey = ""
        }
        val profile = ProfileViewModel(null, storage)
        val network = FakeNetwork(profile::getCoinMarketCapApiKey)
        val coordinator = PortfolioCoordinator(network, profile::getCoinMarketCapApiKey)
        assertEquals(0, network.mapRequests)
        profile.setCoinMarketCapApiKey("placeholder-key")
        coordinator.portfolioViewModel.onAssetSearch()
        assertEquals(listOf("placeholder-key"), network.keys)
        network.complete(NetworkResult(catalog, null))
        assertEquals(PortfolioRoute.ASSET_SEARCH, coordinator.state.value.currentRoute)
        assertFalse(coordinator.assetSearchViewModel.state.value.toString().contains("placeholder-key"))
        coordinator.assetSearchViewModel.onBack()
        assertEquals(PortfolioRoute.PORTFOLIO, coordinator.state.value.currentRoute)
    }

    @Test
    fun generatesAndCachesPublicLogoAddressWithoutMetadataRequests() {
        val network = FakeNetwork()
        val model = AssetSearchViewModel({}, network)
        model.loadCatalog()
        model.loadLogo("1027")
        val state = model.state.value
        model.loadLogo("1027")
        for (id in listOf("", "../1", "1?key=x", "ETH")) model.loadLogo(id)
        assertEquals(state, model.state.value)
        assertEquals(mapOf("1027" to "https://s2.coinmarketcap.com/static/img/coins/64x64/1027.png"),
            model.state.value.logoUrls)
        // Fake metadata methods fail immediately if this path accidentally uses the authenticated API.
        assertEquals(1, network.mapRequests)
    }

    @Test
    fun forwardsImageResultAndHandlesMissingNetworkManager() {
        val network = FakeNetwork()
        val model = AssetSearchViewModel({}, network)
        model.loadCatalog()
        val expected = NetworkResult(byteArrayOf(0, -1, 127), null)
        network.imageResult = expected
        model.loadImage("https://example.com/logo") { assertEquals(expected, it) }
        assertEquals("https://example.com/logo", network.imageURL)
        val missing = AssetSearchViewModel({})
        missing.loadImage("https://example.com/logo") {
            assertNull(it.value)
            assertEquals("Network manager is unavailable", it.error)
        }
        assertFalse(missing.state.value.isLoading)
    }
}

private class FakeNetwork(private val keyProvider: () -> String = { "" }) : NetworkProtocol {
    var mapRequests = 0
    val keys = mutableListOf<String>()
    private val mapCompletions = mutableListOf<(NetworkResult<List<Asset>>) -> Unit>()
    var imageResult = NetworkResult<ByteArray>(null, "Image unavailable")
    var imageURL: String? = null

    override fun fetchMap(completion: (NetworkResult<List<Asset>>) -> Unit) {
        mapRequests++
        keys += keyProvider()
        mapCompletions += completion
    }

    fun complete(result: NetworkResult<List<Asset>>) {
        val completion = mapCompletions.removeAt(0)
        completion(result)
    }

    override fun fetchImg(url: String, completion: (NetworkResult<ByteArray>) -> Unit) {
        imageURL = url
        completion(imageResult)
    }

    override fun fetchLogoURL(id: String, completion: (NetworkResult<String>) -> Unit) =
        error("Unexpected metadata request")

    override fun fetchLogoUrlArray(idString: String, idArray: List<String>,
        completion: (NetworkResult<Map<String, String>>) -> Unit) = error("Unexpected metadata request")

    override fun fetchPriceArray(idString: String, idArray: List<String>,
        completion: (NetworkResult<Map<String, Double>>) -> Unit) = error("Unexpected price request")
}
