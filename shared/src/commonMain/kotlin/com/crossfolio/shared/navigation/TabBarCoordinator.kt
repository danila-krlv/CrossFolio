package com.crossfolio.shared.navigation

import com.crossfolio.shared.analytics.AnalyticsViewModel
import com.crossfolio.shared.portfolio.PortfolioViewModel
import com.crossfolio.shared.profile.ProfileViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTab {
    PORTFOLIO,
    ANALYTICS,
    PROFILE,
}

data class TabBarState(
    val selectedTab: AppTab = AppTab.PORTFOLIO,
)

class TabBarCoordinator(
    val portfolioViewModel: PortfolioViewModel = PortfolioViewModel(),
    val analyticsViewModel: AnalyticsViewModel = AnalyticsViewModel(),
    val profileViewModel: ProfileViewModel = ProfileViewModel(),
) {
    private val _state = MutableStateFlow(TabBarState())
    val state: StateFlow<TabBarState> = _state.asStateFlow()

    fun selectTab(tab: AppTab) {
        _state.value = TabBarState(selectedTab = tab)
    }

    // TODO: Add nested feature navigation when detail and edit flows are implemented.
}
