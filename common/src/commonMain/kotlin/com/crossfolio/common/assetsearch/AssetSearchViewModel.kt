package com.crossfolio.common.assetsearch

import com.crossfolio.common.asset.Asset
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
    private val networkManager: NetworkProtocol? = null,
) {
    private val _state = MutableStateFlow(AssetSearchState())
    val state: StateFlow<AssetSearchState> = _state.asStateFlow()
    private var catalog: List<Asset> = emptyList()
    private var catalogLoaded = false

    init {
        loadCatalog()
    }

    fun loadCatalog() {
        val manager = networkManager ?: return
        if (catalogLoaded || _state.value.isLoading) return
        _state.value = _state.value.copy(isLoading = true, error = null)
        manager.fetchMap { result ->
            val assets = result.value
            if (assets != null) {
                catalog = assets.toList()
                catalogLoaded = true
                _state.value = _state.value.copy(isLoading = false, error = null)
                search(_state.value.searchText)
            } else {
                _state.value = _state.value.copy(isLoading = false, error = result.error)
            }
        }
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
        val manager = networkManager
        if (manager == null) {
            completion(NetworkResult(null, "Network manager is unavailable"))
        } else {
            manager.fetchImg(url, completion)
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

    // TODO: Add asset selection actions.
}
