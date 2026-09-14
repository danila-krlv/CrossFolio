package com.crossfolio.common.portfolio.assetsearch

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.asset.AssetCatalog
import com.crossfolio.common.core.network.NetworkFailure
import com.crossfolio.common.core.network.NetworkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AssetSearchState(
    val message: String = "hello AssetSearchViewModel",
    val searchText: String = "",
    val assets: List<Asset> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val logoUrls: Map<String, String> = emptyMap(),
)

class AssetSearchViewModel(
    private val onBackRequested: () -> Unit,
    private val assetCatalog: AssetCatalog? = null,
    private val imageLoader: ((String, (NetworkResult<ByteArray>) -> Unit) -> Unit)? = null,
    private val onAssetSelected: (Asset) -> Unit = {},
    private val onCatalogFailed: (NetworkFailure?) -> Unit = {},
) {
    private val _state = MutableStateFlow(AssetSearchState())
    val state: StateFlow<AssetSearchState> = _state.asStateFlow()
    private var catalog: List<Asset> = emptyList()
    private var catalogLoaded = false
    private var requestNumber = 0

    fun loadCatalog(forceRefresh: Boolean = false) {
        requestCatalog(forceRefresh)
    }

    private fun requestCatalog(forceRefresh: Boolean) {
        val source = assetCatalog ?: return
        if (!forceRefresh && (catalogLoaded || _state.value.isLoading)) return
        if (forceRefresh) {
            catalog = emptyList()
            catalogLoaded = false
            _state.value = _state.value.copy(assets = emptyList())
        }
        val currentRequest = ++requestNumber
        _state.value = _state.value.copy(isLoading = true, error = null)
        source.fetchMap { result ->
            if (currentRequest != requestNumber) return@fetchMap
            if (result.failure == NetworkFailure.STALE_RESPONSE) {
                _state.value = _state.value.copy(isLoading = false)
                return@fetchMap
            }
            val assets = result.value
            if (assets != null) {
                catalog = assets.toList()
                catalogLoaded = true
                _state.value = _state.value.copy(isLoading = false, error = null)
                search(_state.value.searchText)
            } else {
                _state.value = _state.value.copy(isLoading = false, error = result.error)
                onCatalogFailed(result.failure)
            }
        }
    }

    internal fun resetCatalog() {
        requestNumber++
        catalog = emptyList()
        catalogLoaded = false
        _state.value = AssetSearchState()
    }

    fun search(searchText: String): List<Asset> {
        val assets = catalog.filter { asset ->
            asset.name.startsWith(searchText, ignoreCase = true) ||
                asset.ticker.startsWith(searchText, ignoreCase = true)
        }
        _state.value = _state.value.copy(searchText = searchText, assets = assets)
        return assets
    }

    fun loadLogo(id: String) {
        if (id in _state.value.logoUrls) return
        if (id.isEmpty() || !id.all { it in '0'..'9' }) return
        // CoinMarketCap publishes logos on its CDN by numeric CMC id, without an API request.
        val url = "https://s2.coinmarketcap.com/static/img/coins/64x64/$id.png"
        _state.value = _state.value.copy(logoUrls = _state.value.logoUrls + (id to url))
    }

    fun loadImage(url: String, completion: (NetworkResult<ByteArray>) -> Unit) {
        val loader = imageLoader
        if (loader == null) {
            completion(NetworkResult(null, "Network manager is unavailable"))
        } else {
            loader(url, completion)
        }
    }

    fun observeState(observer: (AssetSearchState) -> Unit): () -> Unit {
        val job = CoroutineScope(Dispatchers.Main.immediate).launch {
            state.collect { observer(it) }
        }
        return { job.cancel() }
    }

    fun onBack() {
        onBackRequested()
    }

    fun selectAsset(asset: Asset) {
        onAssetSelected(asset)
    }
}
