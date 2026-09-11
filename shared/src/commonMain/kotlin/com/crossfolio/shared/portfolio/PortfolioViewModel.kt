package com.crossfolio.shared.portfolio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PortfolioState(
    val message: String = "hello PortfolioViewModel",
)

class PortfolioViewModel {
    private val _state = MutableStateFlow(PortfolioState())
    val state: StateFlow<PortfolioState> = _state.asStateFlow()

    // TODO: Add portfolio data and user actions when its scenarios are implemented.
}
