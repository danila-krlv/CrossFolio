package com.crossfolio.common.assetsearch

import com.crossfolio.common.asset.Asset
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
    fun loadsOnceAndSearchesLocallyByCaseInsensitivePrefix() {
        val network = FakeNetwork()
        val model = AssetSearchViewModel({}, network)
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
        network.complete(NetworkResult(null, "HTTP 401"))
        assertFalse(model.state.value.isLoading)
        assertEquals("HTTP 401", model.state.value.error)
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
        val coordinator = PortfolioCoordinator(network)
        network.complete(NetworkResult(null, "API key is unavailable"))
        profile.setCoinMarketCapApiKey("placeholder-key")
        coordinator.portfolioViewModel.onAssetSearch()
        assertEquals(listOf("", "placeholder-key"), network.keys)
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
    private var mapCompletion: ((NetworkResult<List<Asset>>) -> Unit)? = null
    var imageResult = NetworkResult<ByteArray>(null, "Image unavailable")
    var imageURL: String? = null

    override fun fetchMap(completion: (NetworkResult<List<Asset>>) -> Unit) {
        mapRequests++
        keys += keyProvider()
        mapCompletion = completion
    }

    fun complete(result: NetworkResult<List<Asset>>) {
        val completion = checkNotNull(mapCompletion)
        mapCompletion = null
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
