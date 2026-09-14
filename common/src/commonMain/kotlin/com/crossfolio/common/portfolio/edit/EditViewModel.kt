package com.crossfolio.common.portfolio.edit

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.core.market.AssetQuote
import com.crossfolio.common.portfolio.model.AcquisitionPrice
import com.crossfolio.common.portfolio.model.AcquisitionPriceSource
import com.crossfolio.common.portfolio.model.PortfolioOperation
import com.crossfolio.common.portfolio.model.PortfolioOperationDirection
import com.crossfolio.common.portfolio.model.PortfolioPosition
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
    val marketPriceUsd: DecimalValue,
    val position: PortfolioPosition? = null,
) {
    val canSave: Boolean get() = position != null
}

@OptIn(ExperimentalUuidApi::class)
class EditViewModel(
    val asset: Asset,
    private val onBackRequested: () -> Unit,
    private val rules: AssetFieldRules = AssetFieldRules(),
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val operationId = Uuid.random().toString()
    private val quote = AssetQuote(asset.identity, fetchMarketPrice(), nowEpochMillis())
    private val _state = MutableStateFlow(EditState(
        occurredAtEpochMillis = nowEpochMillis() / 60_000 * 60_000,
        marketPriceUsd = quote.priceUsd,
    ))
    val state: StateFlow<EditState> = _state.asStateFlow()

    init { update(_state.value) }

    fun setQuantity(text: String) = update(_state.value.copy(quantity = EditFieldState(text, true)))
    fun setPrice(text: String) = update(_state.value.copy(price = EditFieldState(text, true)))
    fun setCommission(text: String) = update(_state.value.copy(commission = EditFieldState(text, true)))
    fun setDate(epochMillis: Long) = update(_state.value.copy(
        occurredAtEpochMillis = epochMillis / 60_000 * 60_000, hasEditedDate = true,
    ))

    private fun update(input: EditState) {
        val quantity = runCatching { rules.parseQuantity(input.quantity.text) }
        val price = runCatching {
            if (input.price.text.isBlank()) AcquisitionPrice(quote.priceUsd, AcquisitionPriceSource.MARKET)
            else AcquisitionPrice(rules.parseManualPrice(input.price.text), AcquisitionPriceSource.MANUAL)
        }
        val commission = runCatching { rules.parseCommission(input.commission.text) }
        val now = nowEpochMillis()
        val dateError = if (input.occurredAtEpochMillis > now) "Дата не может быть в будущем" else null
        val position = if (quantity.isSuccess && price.isSuccess && commission.isSuccess && dateError == null) {
            val operation = PortfolioOperation(operationId, PortfolioOperationDirection.ADDITION,
                quantity.getOrThrow(), input.occurredAtEpochMillis, price.getOrThrow(), commission.getOrThrow())
            PortfolioPosition(asset, latestQuote = quote).record(operation, now, rules)
        } else null
        _state.value = input.copy(
            quantity = input.quantity.copy(error = quantity.exceptionOrNull()?.message),
            price = input.price.copy(error = price.exceptionOrNull()?.message),
            commission = input.commission.copy(error = commission.exceptionOrNull()?.message),
            dateError = dateError, position = position,
        )
    }

    private fun fetchMarketPrice(): DecimalValue {
        // TODO: Fetch a real market quote; 100 USD is a temporary UI placeholder.
        return DecimalValue("100")
    }

    fun save() {
        val position = state.value.position ?: return
        println("саксес: ${position.asset.ticker}, ${position.quantity.value}")
        // TODO: Persist state.value.position when saving is implemented.
    }

    fun observeState(observer: (EditState) -> Unit): () -> Unit {
        val job = CoroutineScope(Dispatchers.Main.immediate).launch { state.collect { observer(it) } }
        return { job.cancel() }
    }

    fun onBack() { onBackRequested() }
}
