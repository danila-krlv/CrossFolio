package com.crossfolio.common.portfolio.overview

import com.crossfolio.common.portfolio.model.PortfolioPosition
import com.crossfolio.common.portfolio.storage.PortfolioStorage
import com.crossfolio.common.portfolio.storage.StorageFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PortfolioState(
    val message: String = "hello PortfolioViewModel",
    val positions: List<PortfolioPosition> = emptyList(),
    val isLoading: Boolean = false,
    val storageFailure: StorageFailure? = null,
)

class PortfolioViewModel(
    private val onAssetSearchRequested: () -> Unit,
    private val portfolioStorage: PortfolioStorage? = null,
) {
    private val _state = MutableStateFlow(PortfolioState())
    val state: StateFlow<PortfolioState> = _state.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var loadRequest = 0

    init {
        loadPositions()
    }

    fun onAssetSearch() {
        onAssetSearchRequested()
    }

    fun loadPositions() {
        val storage = portfolioStorage ?: return
        val request = ++loadRequest
        _state.value = _state.value.copy(isLoading = true, storageFailure = null)
        scope.launch {
            val result = storage.loadPositions()
            if (request != loadRequest) return@launch
            _state.value = _state.value.copy(
                positions = result.value ?: _state.value.positions,
                isLoading = false,
                storageFailure = result.failure,
            )
        }
    }
}
