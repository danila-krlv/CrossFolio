package com.crossfolio.common.portfolio.overview

import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.portfolio.model.PortfolioPosition
import com.crossfolio.common.portfolio.storage.PortfolioStorage
import com.crossfolio.common.portfolio.storage.StorageFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PortfolioRowState(
    val position: PortfolioPosition,
    val valueUsd: DecimalValue,
) {
    val valueUsdText: String get() = valueUsd.formatUsd()
}

data class PortfolioState(
    val positions: List<PortfolioPosition> = emptyList(),
    val isLoading: Boolean = false,
    val storageFailure: StorageFailure? = null,
) {
    val rows: List<PortfolioRowState> get() = positions.map { position ->
        PortfolioRowState(
            position = position,
            valueUsd = position.quantity.multiply(position.latestQuote?.priceUsd ?: DecimalValue("1")),
        )
    }
    val totalValueUsd: DecimalValue get() = rows.fold(DecimalValue.ZERO) { total, row ->
        total.add(row.valueUsd)
    }
    val totalValueUsdText: String get() = totalValueUsd.formatUsd()
}

private fun DecimalValue.formatUsd(): String {
    val whole = value.substringBefore('.')
    val fraction = value.substringAfter('.', "")
    val cents = fraction.padEnd(3, '0')
    val rounded = DecimalValue.parse("$whole.${cents.take(2)}").let { truncated ->
        if (cents[2] >= '5') truncated.add(DecimalValue("0.01")) else truncated
    }
    return rounded.value.substringBefore('.') + "." + rounded.value.substringAfter('.', "").padEnd(2, '0')
}

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
