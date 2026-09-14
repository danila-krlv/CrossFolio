package com.crossfolio.common.portfolio.assetsearch

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.network.NetworkProtocol
import com.crossfolio.common.core.network.NetworkResult
import com.crossfolio.common.portfolio.PortfolioCoordinator
import com.crossfolio.common.portfolio.PortfolioRoute
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
        val coordinator = PortfolioCoordinator(network)
        coordinator.setSearchEnabled(true)
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
        assertTrue(coordinator.state.value.isSearchEnabled)
        assertEquals(searchState, search.state.value)
        assertEquals(requestCount, network.mapRequests)
        assertNull(coordinator.editViewModel)
    }

    @Test
    fun loadsOnceAndSearchesLocallyByCaseInsensitivePrefix() {
        val network = FakeNetwork()
        val model = model(network)
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
        val model = model(network)
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
        val model = model(network)
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
        val model = model(network)
        model.loadCatalog()
        network.complete(NetworkResult(emptyList(), null))
        model.loadCatalog()
        assertEquals(1, network.mapRequests)
        assertFalse(model.state.value.isLoading)
        assertNull(model.state.value.error)
        assertTrue(model.search("").isEmpty())
    }

    @Test
    fun generatesAndCachesPublicLogoAddressWithoutMetadataRequests() {
        val network = FakeNetwork()
        val model = model(network)
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
        val model = model(network)
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

private fun model(network: FakeNetwork): AssetSearchViewModel =
    AssetSearchViewModel({}, network, network::fetchImg)

private class FakeNetwork : NetworkProtocol {
    override fun validateApiKey(completion: (NetworkResult<Boolean>) -> Unit) =
        error("Unexpected key validation request")

    var mapRequests = 0
    private val mapCompletions = mutableListOf<(NetworkResult<List<Asset>>) -> Unit>()
    var imageResult = NetworkResult<ByteArray>(null, "Image unavailable")
    var imageURL: String? = null

    override fun fetchMap(completion: (NetworkResult<List<Asset>>) -> Unit) {
        mapRequests++
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
