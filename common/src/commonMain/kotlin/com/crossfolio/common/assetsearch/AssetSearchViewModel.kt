package com.crossfolio.common.assetsearch

import com.crossfolio.common.asset.Asset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AssetSearchState(
    val message: String = "hello AssetSearchViewModel",
    val searchText: String = "",
    val assets: List<Asset> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
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
            asset.name.startsWith(searchText) || asset.ticker.startsWith(searchText)
        }
        _state.value = _state.value.copy(searchText = searchText, assets = assets)
        return assets
    }

    fun onBack() {
        onBackRequested()
    }

    // TODO: Add asset selection actions.
}
