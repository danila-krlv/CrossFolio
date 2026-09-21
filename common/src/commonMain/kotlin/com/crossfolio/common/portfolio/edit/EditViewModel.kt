package com.crossfolio.common.portfolio.edit

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.core.market.AssetQuote
import com.crossfolio.common.core.market.MarketPriceSource
import com.crossfolio.common.core.network.NetworkFailure
import com.crossfolio.common.portfolio.model.AcquisitionPrice
import com.crossfolio.common.portfolio.model.AcquisitionPriceSource
import com.crossfolio.common.portfolio.model.PortfolioOperation
import com.crossfolio.common.portfolio.model.PortfolioOperationDirection
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class EditFieldState(
    val text: String = "",
    val hasEdited: Boolean = false,
    val error: String? = null,
) {
    val isError: Boolean get() = hasEdited && error != null
}

data class EditState(
    val quantity: EditFieldState = EditFieldState(),
    val price: EditFieldState = EditFieldState(),
    val commission: EditFieldState = EditFieldState(),
    val occurredAtEpochMillis: Long,
    val hasEditedDate: Boolean = false,
    val dateError: String? = null,
    val marketPriceUsd: DecimalValue? = null,
    val isMarketPriceLoading: Boolean = false,
    val position: PortfolioPosition? = null,
    val isSaving: Boolean = false,
    val storageFailure: StorageFailure? = null,
) {
    val canSave: Boolean get() = position != null && !isSaving
    val marketPriceUsdText: String? get() = marketPriceUsd?.let(::formatMarketPrice)
}

private fun formatMarketPrice(price: DecimalValue): String {
    val visibleFractionDigits = if (price < DecimalValue("1")) 8 else 2
    if (price.fractionDigits <= visibleFractionDigits) return price.value
    val fraction = price.value.substringAfter('.')
    val shortened = DecimalValue.parse(
        "${price.value.substringBefore('.')}.${fraction.take(visibleFractionDigits)}",
    )
    val roundingUnit = DecimalValue.parse("0." + "0".repeat(visibleFractionDigits - 1) + "1")
    return if (fraction[visibleFractionDigits] >= '5') {
        shortened.add(roundingUnit).value
    } else shortened.value
}

@OptIn(ExperimentalUuidApi::class)
class EditViewModel(
    val asset: Asset,
    private val onBackRequested: () -> Unit,
    private val rules: AssetFieldRules = AssetFieldRules(),
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val marketPriceSource: MarketPriceSource? = null,
    private val onMarketPriceFailed: (NetworkFailure?) -> Unit = {},
    private val portfolioStorage: PortfolioStorage? = null,
    private val onSavedRequested: () -> Unit = {},
) {
    private val operationId = Uuid.random().toString()
    private var quote: AssetQuote? = null
    private val _state = MutableStateFlow(EditState(
        occurredAtEpochMillis = nowEpochMillis() / 60_000 * 60_000,
    ))
    val state: StateFlow<EditState> = _state.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    init {
        update(_state.value)
        update(_state.value.copy(isMarketPriceLoading = true))
        scope.launch {
            quote = portfolioStorage?.loadLastQuote(asset.identity)?.value
            update(_state.value.copy(marketPriceUsd = quote?.priceUsd, isMarketPriceLoading = false))
            if (quote?.let { nowEpochMillis() - it.receivedAtEpochMillis < 600_000 } != true) {
                fetchMarketPrice()
            }
        }
    }

    fun setQuantity(text: String) = edit(_state.value.copy(quantity = EditFieldState(text, true)))
    fun setPrice(text: String) = edit(_state.value.copy(price = EditFieldState(text, true)))
    fun setCommission(text: String) = edit(_state.value.copy(commission = EditFieldState(text, true)))
    fun setDate(epochMillis: Long) = edit(_state.value.copy(
        occurredAtEpochMillis = epochMillis / 60_000 * 60_000, hasEditedDate = true,
    ))

    private fun edit(input: EditState) {
        if (!_state.value.isSaving) update(input)
    }

    private fun update(input: EditState) {
        val quantity = runCatching { rules.parseQuantity(input.quantity.text) }
        val price = runCatching {
            if (input.price.text.isBlank()) AcquisitionPrice(
                requireNotNull(quote?.priceUsd) { "Введите цену вручную" },
                AcquisitionPriceSource.MARKET,
            )
            else AcquisitionPrice(rules.parseManualPrice(input.price.text), AcquisitionPriceSource.MANUAL)
        }
        val commission = runCatching { rules.parseCommission(input.commission.text) }
        val now = nowEpochMillis()
        val dateError = if (input.occurredAtEpochMillis > now) "Дата не может быть в будущем" else null
        val position = if (quantity.isSuccess && price.isSuccess && commission.isSuccess && dateError == null) {
            val operation = PortfolioOperation(operationId, PortfolioOperationDirection.ADDITION,
                quantity.getOrThrow(), input.occurredAtEpochMillis, price.getOrThrow(), commission.getOrThrow())
            PortfolioPosition(asset, latestQuote = quote).record(operation, now, rules.operationRules)
        } else null
        _state.value = input.copy(
            quantity = input.quantity.copy(error = quantity.exceptionOrNull()?.message),
            price = input.price.copy(error = price.exceptionOrNull()?.message),
            commission = input.commission.copy(error = commission.exceptionOrNull()?.message),
            dateError = dateError, position = position,
        )
    }

    private fun fetchMarketPrice() {
        val source = marketPriceSource ?: return
        if (_state.value.isMarketPriceLoading) return
        update(_state.value.copy(isMarketPriceLoading = true))
        source.fetchMarketPrice(asset) { result ->
            val price = result.value
            if (price != null) {
                quote = AssetQuote(asset.identity, price, nowEpochMillis())
                update(_state.value.copy(marketPriceUsd = price, isMarketPriceLoading = false))
            } else {
                update(_state.value.copy(isMarketPriceLoading = false))
                if (result.failure != NetworkFailure.STALE_RESPONSE) {
                    onMarketPriceFailed(result.failure)
                }
            }
        }
    }

    fun save() {
        val position = state.value.position ?: return
        val storage = portfolioStorage ?: return
        if (state.value.isSaving) return
        _state.value = state.value.copy(isSaving = true, storageFailure = null)
        scope.launch {
            val result = storage.savePosition(position)
            _state.value = state.value.copy(isSaving = false, storageFailure = result.failure)
            if (result.failure == null) onSavedRequested()
        }
    }

    fun observeState(observer: (EditState) -> Unit): () -> Unit {
        val job = CoroutineScope(Dispatchers.Main.immediate).launch { state.collect { observer(it) } }
        return { job.cancel() }
    }

    fun onBack() { if (!_state.value.isSaving) onBackRequested() }
}
