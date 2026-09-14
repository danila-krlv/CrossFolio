package com.crossfolio.common.portfolio

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.network.NetworkFailure
import com.crossfolio.common.core.asset.AssetCatalog
import com.crossfolio.common.core.network.NetworkResult
import com.crossfolio.common.portfolio.assetsearch.AssetSearchViewModel
import com.crossfolio.common.portfolio.edit.EditViewModel
import com.crossfolio.common.portfolio.overview.PortfolioViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PortfolioRoute {
    PORTFOLIO,
    ASSET_SEARCH,
    EDIT,
}

data class PortfolioNavigationState(
    val backStack: List<PortfolioRoute> = listOf(PortfolioRoute.PORTFOLIO),
    val isSearchEnabled: Boolean = false,
) {
    init {
        require(backStack.isNotEmpty()) { "Portfolio navigation stack must not be empty" }
    }

    val currentRoute: PortfolioRoute = backStack.last()
}

class PortfolioCoordinator(
    assetCatalog: AssetCatalog? = null,
    imageLoader: ((String, (NetworkResult<ByteArray>) -> Unit) -> Unit)? = null,
) {
    private val _state = MutableStateFlow(PortfolioNavigationState())
    val state: StateFlow<PortfolioNavigationState> = _state.asStateFlow()
    var editViewModel: EditViewModel? = null
        private set

    val portfolioViewModel = PortfolioViewModel(
        onAssetSearchRequested = ::openAssetSearch,
    )
    val assetSearchViewModel = AssetSearchViewModel(
        onBackRequested = ::navigateBack,
        assetCatalog = assetCatalog,
        imageLoader = imageLoader,
        onAssetSelected = ::openEdit,
        onCatalogFailed = { onNetworkFailure(it) },
    )

    internal var onNetworkFailure: (NetworkFailure?) -> Unit = {}

    internal fun setSearchEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(isSearchEnabled = enabled)
    }

    fun resetNavigation() {
        editViewModel = null
        assetSearchViewModel.resetCatalog()
        _state.value = PortfolioNavigationState(isSearchEnabled = _state.value.isSearchEnabled)
    }

    fun openAssetSearch() {
        if (!_state.value.isSearchEnabled) return
        editViewModel = null
        _state.value = _state.value.copy(backStack = listOf(PortfolioRoute.PORTFOLIO, PortfolioRoute.ASSET_SEARCH))
        assetSearchViewModel.loadCatalog()
    }

    fun observeState(observer: (PortfolioNavigationState) -> Unit): () -> Unit {
        val job = CoroutineScope(Dispatchers.Main.immediate).launch {
            state.collect { observer(it) }
        }
        return { job.cancel() }
    }

    private fun openEdit(asset: Asset) {
        if (!_state.value.isSearchEnabled || _state.value.currentRoute != PortfolioRoute.ASSET_SEARCH) return
        editViewModel = EditViewModel(asset, ::navigateBack)
        _state.value = _state.value.copy(backStack = _state.value.backStack + PortfolioRoute.EDIT)
    }

    private fun navigateBack() {
        val backStack = _state.value.backStack
        if (backStack.size > 1) {
            editViewModel = null
            _state.value = _state.value.copy(backStack = backStack.dropLast(1))
        }
    }
}
