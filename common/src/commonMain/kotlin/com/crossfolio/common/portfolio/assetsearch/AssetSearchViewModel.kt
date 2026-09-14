package com.crossfolio.common.portfolio.assetsearch

import com.crossfolio.common.core.asset.Asset
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
    val isApiKeyInvalid: Boolean = false,
)

class AssetSearchViewModel(
    private val onBackRequested: () -> Unit,
    private val networkManager: NetworkProtocol? = null,
    private val apiKeyProvider: (() -> String)? = null,
    private val onAssetSelected: (Asset) -> Unit = {},
) {
    private val _state = MutableStateFlow(AssetSearchState())
    val state: StateFlow<AssetSearchState> = _state.asStateFlow()
    private var catalog: List<Asset> = emptyList()
    private var catalogLoaded = false
    private var requestNumber = 0

    fun openSearch() {
        openSearch {}
    }

    fun openSearch(onValidated: (Boolean) -> Unit) {
        val key = apiKeyProvider?.invoke()?.trim().orEmpty()
        if (key.isEmpty() || key.any { it.isWhitespace() }) {
            requestNumber++
            catalog = emptyList()
            catalogLoaded = false
            _state.value = _state.value.copy(assets = emptyList(), isLoading = false,
                error = "API key is missing or malformed", isApiKeyInvalid = true)
            onValidated(false)
            return
        }
        if (networkManager == null) {
            _state.value = _state.value.copy(error = "Network manager is unavailable", isApiKeyInvalid = false)
            onValidated(false)
            return
        }
        requestCatalog(forceRefresh = true, onValidated)
    }

    fun loadCatalog(forceRefresh: Boolean = false) {
        requestCatalog(forceRefresh) {}
    }

    private fun requestCatalog(forceRefresh: Boolean, onValidated: (Boolean) -> Unit) {
        val manager = networkManager ?: return
        if (!forceRefresh && (catalogLoaded || _state.value.isLoading)) return
        if (forceRefresh) {
            catalog = emptyList()
            catalogLoaded = false
            _state.value = _state.value.copy(assets = emptyList())
        }
        val currentRequest = ++requestNumber
        val requestKey = apiKeyProvider?.invoke()
        _state.value = _state.value.copy(isLoading = true, error = null, isApiKeyInvalid = false)
        manager.fetchMap { result ->
            if (currentRequest != requestNumber) return@fetchMap
            if (requestKey != apiKeyProvider?.invoke()) {
                _state.value = _state.value.copy(isLoading = false)
                return@fetchMap
            }
            val assets = result.value
            if (assets != null) {
                catalog = assets.toList()
                catalogLoaded = true
                _state.value = _state.value.copy(isLoading = false, error = null)
                search(_state.value.searchText)
                onValidated(true)
            } else {
                _state.value = _state.value.copy(isLoading = false, error = result.error)
                val invalidKey = result.error?.let { message ->
                    message.startsWith("HTTP 401:") || listOf(1001, 1002, 1005, 1007).any {
                        message.startsWith("CoinMarketCap error $it:")
                    }
                } == true
                _state.value = _state.value.copy(isApiKeyInvalid = invalidKey)
                onValidated(false)
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

    fun selectAsset(asset: Asset) {
        onAssetSelected(asset)
    }
}
