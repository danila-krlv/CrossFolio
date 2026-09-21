package com.crossfolio.common.analytics

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.portfolio.model.PortfolioPosition
import com.crossfolio.common.portfolio.overview.PortfolioState
import com.crossfolio.common.portfolio.overview.PortfolioViewModel
import com.crossfolio.common.portfolio.overview.formatUsd
import com.crossfolio.common.portfolio.storage.PortfolioStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** An observed valuation, not a reconstruction of historical market prices. */
data class PortfolioSnapshot(val observedAtEpochMillis: Long, val valueUsd: DecimalValue) {
    val valueUsdText: String get() = valueUsd.formatUsd()
    val chartValue: Double get() = valueUsd.value.toDouble()
}

data class AssetShare(val asset: Asset, val tenthsPercent: Int) {
    val fraction: Double get() = tenthsPercent / 1000.0
    val percentText: String get() = when {
        tenthsPercent == 0 -> "<0.1%"
        tenthsPercent % 10 == 0 -> "${tenthsPercent / 10}%"
        else -> "${tenthsPercent / 10}.${tenthsPercent % 10}%"
    }
}

data class AnalyticsState(
    val shares: List<AssetShare> = emptyList(),
    val history: List<PortfolioSnapshot> = emptyList(),
    val isLoading: Boolean = false,
    val missingQuotes: Boolean = false,
    val errorMessage: String? = null,
)

/** Do not pass the portfolio's synthetic $1 fallback off as a market quote. */
internal fun marketValue(positions: List<PortfolioPosition>): DecimalValue? {
    var total = DecimalValue.ZERO
    for (position in positions) {
        if (position.quantity.isZero) continue
        val quote = position.latestQuote ?: return null
        total = total.add(position.quantity.multiply(quote.priceUsd))
    }
    return total
}

internal fun assetShares(positions: List<PortfolioPosition>): List<AssetShare> {
    val total = marketValue(positions) ?: return emptyList()
    if (total.isZero) return emptyList()
    val rows = positions.mapNotNull { position ->
        val value = position.latestQuote?.let { position.quantity.multiply(it.priceUsd) }
        if (value == null || value.isZero) null else position.asset to value
    }.sortedWith(compareByDescending<Pair<Asset, DecimalValue>> { it.second }
        .thenBy { it.first.searchPlatform.name }.thenBy { it.first.searchId })
    // Largest remainders allocate exactly 100.0%, without floating-point money arithmetic.
    val scaled = rows.map { it.second.multiply(DecimalValue("1000")) }
    val units = scaled.map { value ->
        var low = 0
        var high = 1000
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (total.multiply(DecimalValue(mid.toString())) <= value) low = mid else high = mid - 1
        }
        low
    }.toMutableList()
    val remainders = scaled.indices.sortedByDescending { index ->
        scaled[index].subtract(total.multiply(DecimalValue(units[index].toString())))
    }
    remainders.take(1000 - units.sum()).forEach { units[it]++ }
    return rows.mapIndexed { index, row -> AssetShare(row.first, units[index]) }
}

class AnalyticsViewModel(
    private val portfolioViewModel: PortfolioViewModel? = null,
    private val portfolioStorage: PortfolioStorage? = null,
) {
    private val _state = MutableStateFlow(AnalyticsState())
    val state: StateFlow<AnalyticsState> = _state.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private var historyRequest = 0

    init {
        scope.launch {
            portfolioViewModel?.state?.collect { portfolio ->
                updatePortfolio(portfolio)
                if (!portfolio.isLoading) loadHistory()
            }
        }
    }

    fun refresh() {
        portfolioViewModel?.loadPositions()
        scope.launch { loadHistory() }
    }

    private fun updatePortfolio(portfolio: PortfolioState) {
        _state.value = _state.value.copy(
            shares = assetShares(portfolio.positions),
            missingQuotes = marketValue(portfolio.positions) == null,
            isLoading = portfolio.isLoading,
            errorMessage = if (portfolio.storageFailure != null) "Не удалось обновить данные портфеля." else null,
        )
    }

    private suspend fun loadHistory() {
        val request = ++historyRequest
        val result = portfolioStorage?.loadSnapshots() ?: return
        if (request != historyRequest) return
        _state.value = _state.value.copy(
            history = result.value ?: _state.value.history,
            errorMessage = if (result.failure != null) "Не удалось загрузить историю стоимости." else if (portfolioViewModel?.state?.value?.storageFailure != null) "Не удалось обновить данные портфеля." else null,
        )
    }

    fun observeState(observer: (AnalyticsState) -> Unit): () -> Unit {
        val job = scope.launch { state.collect { observer(it) } }
        return { job.cancel() }
    }
}
