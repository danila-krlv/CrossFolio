package com.crossfolio.common.portfolio.edit

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.asset.AssetIdentity
import com.crossfolio.common.core.market.AssetQuote
import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.core.market.MarketPriceSource
import com.crossfolio.common.core.network.NetworkFailure
import com.crossfolio.common.core.network.NetworkResult
import com.crossfolio.common.portfolio.model.PortfolioOperationRules
import com.crossfolio.common.portfolio.model.AcquisitionPriceSource
import com.crossfolio.common.portfolio.model.PortfolioPosition
import com.crossfolio.common.portfolio.storage.PortfolioStorage
import com.crossfolio.common.portfolio.storage.StorageFailure
import com.crossfolio.common.portfolio.storage.StorageResult
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditViewModelTest {
    @Test
    fun freshSavedQuoteAvoidsNetworkAndPreservesExactAcquisitionPrice() {
        val asset = Asset("1", "BTC")
        val cached = AssetQuote(asset.identity, DecimalValue("12.345678901"), 700_000)
        val source = ManualMarketPriceSource()
        val model = EditViewModel(asset, {}, nowEpochMillis = { 1_200_000L },
            marketPriceSource = source, portfolioStorage = RecordingPortfolioStorage(cachedQuote = cached))
        model.setQuantity("1")
        assertEquals(0, source.requests)
        assertFalse(model.state.value.isMarketPriceLoading)
        assertEquals(cached, model.state.value.position?.latestQuote)
        assertEquals(cached.priceUsd, model.state.value.position?.operations?.single()?.acquisitionPrice?.usd)
    }

    @Test
    fun staleQuoteRemainsUsableWhenRefreshFails() {
        val asset = Asset("1", "BTC")
        val cached = AssetQuote(asset.identity, DecimalValue("12.345678901"), 600_000)
        val source = ManualMarketPriceSource()
        val model = EditViewModel(asset, {}, nowEpochMillis = { 1_200_000L },
            marketPriceSource = source, portfolioStorage = RecordingPortfolioStorage(cachedQuote = cached))
        model.setQuantity("1")
        assertEquals(1, source.requests)
        assertTrue(model.state.value.canSave)
        source.fail()
        assertFalse(model.state.value.isMarketPriceLoading)
        assertEquals(cached.priceUsd, model.state.value.marketPriceUsd)
        assertEquals(cached, model.state.value.position?.latestQuote)
        assertTrue(model.state.value.canSave)
    }

    private fun model() = EditViewModel(
        Asset("1", "BTC"), {}, nowEpochMillis = { 120_000L }, marketPriceSource = FixedMarketPriceSource,
    )

    @Test
    fun formAndPositionUseTheSameCustomOperationLimits() {
        val rules = AssetFieldRules(PortfolioOperationRules(
            quantityFractionDigits = 0, maximumInputValue = DecimalValue("2"),
        ))
        val model = EditViewModel(
            Asset("1", "BTC"), {}, rules, nowEpochMillis = { 120_000L },
            marketPriceSource = FixedMarketPriceSource,
        )
        model.setQuantity("1.1")
        assertTrue(model.state.value.quantity.isError)
        model.setQuantity("3")
        assertFalse(model.state.value.canSave)
        model.setQuantity("2")
        assertEquals(DecimalValue("2"), model.state.value.position?.quantity)
        model.setPrice("3")
        assertTrue(model.state.value.price.isError)
        model.setPrice("2")
        model.setCommission("3")
        assertTrue(model.state.value.commission.isError)
        model.setCommission("2")
        assertTrue(model.state.value.canSave)
    }

    @Test
    fun touchedFieldsAndPreparedPositionFollowFormValidity() {
        val model = model()
        assertFalse(model.state.value.canSave)
        assertFalse(model.state.value.quantity.isError)
        model.setQuantity("0")
        assertTrue(model.state.value.quantity.isError)
        model.setQuantity("1,25")
        val position = requireNotNull(model.state.value.position)
        assertEquals(DecimalValue("1.25"), position.quantity)
        assertEquals(DecimalValue("100"), position.operations.single().acquisitionPrice?.usd)
        assertEquals(AcquisitionPriceSource.MARKET, position.operations.single().acquisitionPrice?.source)
        val id = position.operations.single().id
        model.setPrice("123,45")
        assertEquals(AcquisitionPriceSource.MANUAL,
            model.state.value.position?.operations?.single()?.acquisitionPrice?.source)
        assertEquals(id, model.state.value.position?.operations?.single()?.id)
        model.setPrice("0")
        assertTrue(model.state.value.price.isError)
        assertNull(model.state.value.position)
        model.setPrice("")
        model.setCommission("0,001")
        assertTrue(model.state.value.commission.isError)
        model.setCommission("")
        assertEquals(DecimalValue.ZERO, model.state.value.position?.operations?.single()?.commissionUsd)
        model.setQuantity("")
        assertTrue(model.state.value.quantity.isError)
        assertFalse(model.state.value.canSave)
    }

    @Test
    fun futureDateAndExcessPrecisionDisableSave() {
        val model = model()
        model.setQuantity("0.000000001")
        assertFalse(model.state.value.canSave)
        model.setQuantity("10000000.01")
        assertFalse(model.state.value.canSave)
        model.setQuantity("1")
        model.setDate(180_000)
        assertFalse(model.state.value.canSave)
        assertTrue(model.state.value.dateError != null)
        model.setDate(60_000)
        assertTrue(model.state.value.canSave)
        model.save()
        assertTrue(model.state.value.canSave)
    }

    @Test
    fun savePersistsPreparedPositionAndReportsCompletion() {
        val storage = RecordingPortfolioStorage()
        var saved = false
        val model = EditViewModel(
            Asset("1", "BTC"), {}, nowEpochMillis = { 120_000L },
            marketPriceSource = FixedMarketPriceSource,
            portfolioStorage = storage,
            onSavedRequested = { saved = true },
        )
        model.setQuantity("1")

        model.save()

        assertEquals(model.state.value.position, storage.savedPosition)
        assertTrue(saved)
        assertFalse(model.state.value.isSaving)
        assertNull(model.state.value.storageFailure)
    }

    @Test
    fun pendingSaveFreezesFormAndBackAndAllowsRetryAfterFailure() {
        val storage = RecordingPortfolioStorage(StorageFailure.WRITE, deferSave = true)
        var backs = 0
        val model = EditViewModel(
            Asset("1", "BTC"), { backs++ }, nowEpochMillis = { 120_000L },
            marketPriceSource = FixedMarketPriceSource, portfolioStorage = storage,
        )
        model.setQuantity("1")
        model.save()
        assertTrue(model.state.value.isSaving)
        assertFalse(model.state.value.canSave)
        model.setQuantity("2")
        model.setPrice("5")
        model.setCommission("1")
        model.setDate(60_000)
        model.onBack()
        model.save()
        assertEquals("1", model.state.value.quantity.text)
        assertEquals("", model.state.value.price.text)
        assertEquals("", model.state.value.commission.text)
        assertEquals(120_000L, model.state.value.occurredAtEpochMillis)
        assertEquals(0, backs)
        assertEquals(1, storage.saveCount)
        storage.pendingSave!!.resume(Unit)
        assertFalse(model.state.value.isSaving)
        assertEquals(StorageFailure.WRITE, model.state.value.storageFailure)
        model.setQuantity("2")
        assertEquals("2", model.state.value.quantity.text)
        assertTrue(model.state.value.canSave)
    }

    @Test
    fun saveFailureStaysInEditState() {
        val storage = RecordingPortfolioStorage(StorageFailure.WRITE)
        var saved = false
        val model = EditViewModel(
            Asset("1", "BTC"), {}, nowEpochMillis = { 120_000L },
            marketPriceSource = FixedMarketPriceSource,
            portfolioStorage = storage,
            onSavedRequested = { saved = true },
        )
        model.setQuantity("1")

        model.save()

        assertFalse(saved)
        assertFalse(model.state.value.isSaving)
        assertEquals(StorageFailure.WRITE, model.state.value.storageFailure)
    }

    @Test
    fun fetchedMarketPriceEnablesSavingWithoutManualPrice() {
        val source = ManualMarketPriceSource()
        val model = EditViewModel(
            Asset("1", "BTC"), {}, nowEpochMillis = { 120_000L }, marketPriceSource = source,
        )
        model.setQuantity("1")
        assertTrue(model.state.value.isMarketPriceLoading)
        assertFalse(model.state.value.canSave)

        source.complete(DecimalValue("12.3456789"))

        assertEquals(DecimalValue("12.3456789"), model.state.value.marketPriceUsd)
        assertFalse(model.state.value.isMarketPriceLoading)
        assertEquals(
            AcquisitionPriceSource.MARKET,
            model.state.value.position?.operations?.single()?.acquisitionPrice?.source,
        )
        assertTrue(model.state.value.canSave)
    }

    @Test
    fun marketPriceTextUsesEightDigitsBelowOneAndTwoDigitsOtherwise() {
        fun format(value: String) = EditState(
            occurredAtEpochMillis = 0,
            marketPriceUsd = DecimalValue.parse(value),
        ).marketPriceUsdText

        assertEquals("123.46", format("123.456"))
        assertEquals("123.4", format("123.404"))
        assertEquals("123", format("123.004"))
        assertEquals("10", format("9.999"))
        assertEquals("0.123456", format("0.123456"))
        assertEquals("0.12345679", format("0.123456789"))
        assertEquals("0.0012", format("0.00120000"))
        assertEquals("0", format("0.000000001"))
        assertEquals("1", format("1"))
    }
}

private class RecordingPortfolioStorage(
    private val saveFailure: StorageFailure? = null,
    private val deferSave: Boolean = false,
    private val cachedQuote: AssetQuote? = null,
) : PortfolioStorage {
    var savedPosition: PortfolioPosition? = null
    var pendingSave: Continuation<Unit>? = null
    var saveCount = 0

    override suspend fun loadPositions() = StorageResult(emptyList<PortfolioPosition>(), null)

    override suspend fun savePosition(position: PortfolioPosition): StorageResult<Unit> {
        saveCount++
        savedPosition = position
        if (deferSave) suspendCoroutine<Unit> { pendingSave = it }
        return if (saveFailure == null) StorageResult(Unit, null) else StorageResult(null, saveFailure)
    }

    override suspend fun deletePosition(assetIdentity: AssetIdentity) = StorageResult(Unit, null)

    override suspend fun loadLastQuote(assetIdentity: AssetIdentity) =
        if (cachedQuote != null) StorageResult(cachedQuote, null)
        else StorageResult<AssetQuote>(null, StorageFailure.NOT_FOUND)

    override suspend fun saveLastQuote(quote: AssetQuote) = StorageResult(Unit, null)

    override suspend fun loadSnapshots() = com.crossfolio.common.portfolio.storage.StorageResult(
        emptyList<com.crossfolio.common.analytics.PortfolioSnapshot>(), null,
    )

    override fun close() = Unit
}

private object FixedMarketPriceSource : MarketPriceSource {
    override fun fetchMarketPrice(asset: Asset, completion: (NetworkResult<DecimalValue>) -> Unit) {
        completion(NetworkResult(DecimalValue("100"), null))
    }
}

private class ManualMarketPriceSource : MarketPriceSource {
    var requests = 0
    private lateinit var completion: (NetworkResult<DecimalValue>) -> Unit

    override fun fetchMarketPrice(asset: Asset, completion: (NetworkResult<DecimalValue>) -> Unit) {
        requests++
        this.completion = completion
    }

    fun fail() { completion(NetworkResult(null, "Offline", NetworkFailure.TRANSPORT)) }

    fun complete(price: DecimalValue) {
        completion(NetworkResult(price, null))
    }
}
