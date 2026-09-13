package com.crossfolio.common.navigation

import com.crossfolio.common.analytics.AnalyticsViewModel
import com.crossfolio.common.portfolio.PortfolioCoordinator
import com.crossfolio.common.portfolio.PortfolioRoute
import com.crossfolio.common.profile.ProfileViewModel
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
    val portfolioCoordinator: PortfolioCoordinator = PortfolioCoordinator(),
    val analyticsViewModel: AnalyticsViewModel = AnalyticsViewModel(),
    val profileViewModel: ProfileViewModel = ProfileViewModel(),
) {
    private val _state = MutableStateFlow(TabBarState())
    val state: StateFlow<TabBarState> = _state.asStateFlow()

    fun selectTab(tab: AppTab) {
        if (tab == AppTab.PORTFOLIO && _state.value.selectedTab != AppTab.PORTFOLIO &&
            portfolioCoordinator.state.value.currentRoute == PortfolioRoute.ASSET_SEARCH
        ) {
            portfolioCoordinator.openAssetSearch()
        }
        _state.value = TabBarState(selectedTab = tab)
    }

    // TODO: Add nested feature navigation when detail and edit flows are implemented.
}
