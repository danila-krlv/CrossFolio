package com.crossfolio.common.portfolio.overview

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.asset.AssetIdentity
import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.core.market.AssetQuote
import com.crossfolio.common.core.market.MarketPriceSource
import com.crossfolio.common.core.network.NetworkFailure
import com.crossfolio.common.core.network.NetworkResult
import com.crossfolio.common.portfolio.model.PortfolioPosition
import com.crossfolio.common.portfolio.storage.PortfolioStorage
import com.crossfolio.common.portfolio.storage.StorageFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

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

internal fun DecimalValue.formatUsd(): String {
    if (!isZero && this < DecimalValue("0.01")) return "<\$0.01"
    val whole = value.substringBefore('.')
    val fraction = value.substringAfter('.', "")
    val cents = fraction.padEnd(3, '0')
    val rounded = DecimalValue.parse("$whole.${cents.take(2)}").let { truncated ->
        if (cents[2] >= '5') truncated.add(DecimalValue("0.01")) else truncated
    }
    return "\$" + rounded.value.substringBefore('.') + "." + rounded.value.substringAfter('.', "").padEnd(2, '0')
}

class PortfolioViewModel(
    private val onAssetSearchRequested: () -> Unit,
    private val portfolioStorage: PortfolioStorage? = null,
    private val imageLoader: ((String, (NetworkResult<ByteArray>) -> Unit) -> Unit)? = null,
    private val logoUrlProvider: (Asset) -> String? = { null },
    private val marketPriceSource: MarketPriceSource? = null,
    private val canRefreshPrices: () -> Boolean = { true },
    private val onMarketPriceFailed: (NetworkFailure?) -> Unit = {},
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val _state = MutableStateFlow(PortfolioState())
    val state: StateFlow<PortfolioState> = _state.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var loadRequest = 0
    private var priceGeneration = 0
    private val inFlight = mutableSetOf<AssetIdentity>()
    private val failedAt = mutableMapOf<AssetIdentity, Long>()

    init {
        loadPositions()
    }

    fun onAssetSearch() {
        onAssetSearchRequested()
    }

    fun logoUrl(asset: Asset): String? = logoUrlProvider(asset)

    fun loadImage(url: String, completion: (NetworkResult<ByteArray>) -> Unit) {
        val loader = imageLoader
        if (loader == null) {
            completion(NetworkResult(null, "Network manager is unavailable"))
        } else {
            loader(url, completion)
        }
    }

    fun loadPositions() {
        val storage = portfolioStorage ?: return
        val request = ++loadRequest
        _state.value = _state.value.copy(isLoading = true, storageFailure = null)
        scope.launch {
            val result = storage.loadPositions()
            if (request != loadRequest) return@launch
            _state.value = _state.value.copy(
                positions = result.value?.map { loaded ->
                    val currentQuote = _state.value.positions.find { it.asset.identity == loaded.asset.identity }?.latestQuote
                    if (currentQuote != null && currentQuote.receivedAtEpochMillis >
                        (loaded.latestQuote?.receivedAtEpochMillis ?: Long.MIN_VALUE)) {
                        PortfolioPosition(loaded.asset, loaded.operations, currentQuote)
                    } else loaded
                } ?: _state.value.positions,
                isLoading = false,
                storageFailure = result.failure,
            )
            if (result.value != null) refreshPrices()
        }
    }

    internal fun invalidatePriceRequests() {
        priceGeneration++
        inFlight.clear()
        failedAt.clear()
    }

    private fun refreshPrices() {
        val source = marketPriceSource ?: return
        if (!canRefreshPrices()) return
        val generation = priceGeneration
        val now = nowEpochMillis()
        val positions = _state.value.positions.filter { position ->
            val identity = position.asset.identity
            position.latestQuote?.let { now - it.receivedAtEpochMillis < 600_000 } != true &&
                failedAt[identity]?.let { now - it < 60_000 } != true && inFlight.add(identity)
        }
        var remaining = positions.size
        val quotes = mutableListOf<AssetQuote>()
        for (position in positions) {
            if (generation != priceGeneration || !canRefreshPrices()) break
            val identity = position.asset.identity
            source.fetchMarketPrice(position.asset) { result ->
                if (generation != priceGeneration) return@fetchMarketPrice
                val price = result.value
                if (price == null) {
                    inFlight.remove(identity)
                    if (result.failure != NetworkFailure.STALE_RESPONSE) {
                        failedAt[identity] = nowEpochMillis()
                        onMarketPriceFailed(result.failure)
                    }
                } else {
                    quotes += AssetQuote(identity, price, nowEpochMillis())
                }
                remaining--
                if (remaining != 0 || quotes.isEmpty() || generation != priceGeneration) return@fetchMarketPrice
                scope.launch {
                    val saved = portfolioStorage?.saveLastQuotes(quotes)
                    if (generation != priceGeneration) return@launch
                    val byIdentity = quotes.associateBy { it.assetIdentity }
                    inFlight.removeAll(byIdentity.keys)
                    byIdentity.keys.forEach { failedAt.remove(it) }
                    _state.value = _state.value.copy(
                        positions = _state.value.positions.map { current ->
                            val quote = byIdentity[current.asset.identity]
                            if (quote != null &&
                                (current.latestQuote?.receivedAtEpochMillis ?: Long.MIN_VALUE) <= quote.receivedAtEpochMillis) {
                                PortfolioPosition(current.asset, current.operations, quote)
                            } else current
                        },
                        storageFailure = saved?.failure ?: _state.value.storageFailure,
                    )
                }
            }
        }
    }

    fun observeState(observer: (PortfolioState) -> Unit): () -> Unit {
        val job = CoroutineScope(Dispatchers.Main.immediate).launch { state.collect { observer(it) } }
        return { job.cancel() }
    }
}
