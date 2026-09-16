package com.crossfolio.common.portfolio

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.network.NetworkFailure
import com.crossfolio.common.core.asset.AssetCatalog
import com.crossfolio.common.core.market.MarketPriceSource
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
    logoUrlProvider: (Asset) -> String? = { null },
    private val marketPriceSource: MarketPriceSource? = null,
) {
    private val _state = MutableStateFlow(PortfolioNavigationState())
    val state: StateFlow<PortfolioNavigationState> = _state.asStateFlow()
    private var editSession: Any? = null
    var editViewModel: EditViewModel? = null
        private set

    val portfolioViewModel = PortfolioViewModel(
        onAssetSearchRequested = ::openAssetSearch,
    )
    val assetSearchViewModel = AssetSearchViewModel(
        onBackRequested = ::navigateBack,
        assetCatalog = assetCatalog,
        imageLoader = imageLoader,
        logoUrlProvider = logoUrlProvider,
        onAssetSelected = ::openEdit,
        onCatalogFailed = { onNetworkFailure(it) },
    )

    internal var onNetworkFailure: (NetworkFailure?) -> Unit = {}

    internal fun setSearchEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(isSearchEnabled = enabled)
    }

    fun resetNavigation() {
        editSession = null
        editViewModel = null
        assetSearchViewModel.resetCatalog()
        _state.value = PortfolioNavigationState(isSearchEnabled = _state.value.isSearchEnabled)
    }

    fun openAssetSearch() {
        if (!_state.value.isSearchEnabled) return
        editSession = null
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
        val session = Any()
        editSession = session
        editViewModel = EditViewModel(
            asset,
            ::navigateBack,
            marketPriceSource = marketPriceSource,
            onMarketPriceFailed = { failure ->
                if (editSession === session) onNetworkFailure(failure)
            },
        )
        _state.value = _state.value.copy(backStack = _state.value.backStack + PortfolioRoute.EDIT)
        editViewModel?.fetchMarketPrice()
    }

    private fun navigateBack() {
        val backStack = _state.value.backStack
        if (backStack.size > 1) {
            editSession = null
            editViewModel = null
            _state.value = _state.value.copy(backStack = backStack.dropLast(1))
        }
    }
}
