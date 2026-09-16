package com.crossfolio.common.portfolio.overview

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PortfolioState(
    val message: String = "hello PortfolioViewModel",
)

class PortfolioViewModel(
    private val onAssetSearchRequested: () -> Unit,
) {
    private val _state = MutableStateFlow(PortfolioState())
    val state: StateFlow<PortfolioState> = _state.asStateFlow()

    fun onAssetSearch() {
        onAssetSearchRequested()
    }

    // TODO: Add portfolio data and user actions when its scenarios are implemented.
}
