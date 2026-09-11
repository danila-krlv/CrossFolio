package com.crossfolio.common.analytics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AnalyticsState(
    val message: String = "hello AnalyticsViewModel",
)

class AnalyticsViewModel {
    private val _state = MutableStateFlow(AnalyticsState())
    val state: StateFlow<AnalyticsState> = _state.asStateFlow()

    // TODO: Define analytics data and actions after the product scope is agreed.
}
