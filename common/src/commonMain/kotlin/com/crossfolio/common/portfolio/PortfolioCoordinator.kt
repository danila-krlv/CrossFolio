package com.crossfolio.common.portfolio

import com.crossfolio.common.assetsearch.AssetSearchViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PortfolioRoute {
    PORTFOLIO,
    ASSET_SEARCH,
}

data class PortfolioNavigationState(
    val backStack: List<PortfolioRoute> = listOf(PortfolioRoute.PORTFOLIO),
) {
    init {
        require(backStack.isNotEmpty()) { "Portfolio navigation stack must not be empty" }
    }

    val currentRoute: PortfolioRoute = backStack.last()
}

class PortfolioCoordinator {
    private val _state = MutableStateFlow(PortfolioNavigationState())
    val state: StateFlow<PortfolioNavigationState> = _state.asStateFlow()

    val portfolioViewModel = PortfolioViewModel(
        onAssetSearchRequested = ::openAssetSearch,
    )
    val assetSearchViewModel = AssetSearchViewModel(
        onBackRequested = ::navigateBack,
    )

    private fun openAssetSearch() {
        _state.value = PortfolioNavigationState(
            backStack = _state.value.backStack + PortfolioRoute.ASSET_SEARCH,
        )
    }

    private fun navigateBack() {
        val backStack = _state.value.backStack
        if (backStack.size > 1) {
            _state.value = PortfolioNavigationState(backStack = backStack.dropLast(1))
        }
    }

    // TODO: Add asset details and quantity editing routes.
}
