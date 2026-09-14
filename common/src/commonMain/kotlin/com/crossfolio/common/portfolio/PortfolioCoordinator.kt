package com.crossfolio.common.portfolio

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.portfolio.assetsearch.AssetSearchViewModel
import com.crossfolio.common.core.network.NetworkProtocol
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
    val alertMessage: String? = null,
) {
    init {
        require(backStack.isNotEmpty()) { "Portfolio navigation stack must not be empty" }
    }

    val currentRoute: PortfolioRoute = backStack.last()
}

class PortfolioCoordinator(
    networkManager: NetworkProtocol? = null,
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
        assetCatalog = networkManager,
        imageLoader = networkManager?.let { it::fetchImg },
        onAssetSelected = ::openEdit,
    )

    private var navigationVersion = 0

    init {
        val version = navigationVersion
        assetSearchViewModel.openSearch { valid ->
            if (version != navigationVersion) return@openSearch
            if (!valid) showValidationAlert()
        }
    }

    fun resetNavigation() {
        navigationVersion++
        editViewModel = null
        _state.value = PortfolioNavigationState()
    }

    fun openAssetSearch() {
        val version = ++navigationVersion
        editViewModel = null
        _state.value = _state.value.copy(backStack = listOf(PortfolioRoute.PORTFOLIO), alertMessage = null)
        assetSearchViewModel.openSearch { valid ->
            if (version != navigationVersion) return@openSearch
            if (valid) {
                _state.value = _state.value.copy(
                    backStack = listOf(PortfolioRoute.PORTFOLIO, PortfolioRoute.ASSET_SEARCH),
                    alertMessage = null,
                )
            } else {
                showValidationAlert()
            }
        }
    }

    fun dismissAlert() {
        _state.value = _state.value.copy(alertMessage = null)
    }

    private fun showValidationAlert() {
        val message = if (assetSearchViewModel.state.value.isApiKeyInvalid) {
            "Добавьте действительный API-ключ CoinMarketCap в профиле."
        } else {
            "Не удалось проверить API-ключ. Проверьте подключение к интернету и повторите попытку."
        }
        _state.value = _state.value.copy(alertMessage = message)
    }

    fun observeState(observer: (PortfolioNavigationState) -> Unit): () -> Unit {
        val job = CoroutineScope(Dispatchers.Main.immediate).launch {
            state.collect { observer(it) }
        }
        return { job.cancel() }
    }

    private fun openEdit(asset: Asset) {
        if (_state.value.currentRoute != PortfolioRoute.ASSET_SEARCH) return
        editViewModel = EditViewModel(asset, ::navigateBack)
        _state.value = _state.value.copy(backStack = _state.value.backStack + PortfolioRoute.EDIT)
    }

    private fun navigateBack() {
        val backStack = _state.value.backStack
        if (backStack.size > 1) {
            editViewModel = null
            _state.value = PortfolioNavigationState(backStack = backStack.dropLast(1))
        }
    }
}
