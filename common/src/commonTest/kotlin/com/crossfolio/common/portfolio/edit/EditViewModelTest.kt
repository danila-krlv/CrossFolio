package com.crossfolio.common.portfolio.edit

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.asset.AssetIdentity
import com.crossfolio.common.core.market.AssetQuote
import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.core.market.MarketPriceSource
import com.crossfolio.common.core.network.NetworkResult
import com.crossfolio.common.portfolio.model.PortfolioOperationRules
import com.crossfolio.common.portfolio.model.AcquisitionPriceSource
import com.crossfolio.common.portfolio.model.PortfolioPosition
import com.crossfolio.common.portfolio.storage.PortfolioStorage
import com.crossfolio.common.portfolio.storage.StorageFailure
import com.crossfolio.common.portfolio.storage.StorageResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditViewModelTest {
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
) : PortfolioStorage {
    var savedPosition: PortfolioPosition? = null

    override suspend fun loadPositions() = StorageResult(emptyList<PortfolioPosition>(), null)

    override suspend fun savePosition(position: PortfolioPosition): StorageResult<Unit> {
        savedPosition = position
        return if (saveFailure == null) StorageResult(Unit, null) else StorageResult(null, saveFailure)
    }

    override suspend fun deletePosition(assetIdentity: AssetIdentity) = StorageResult(Unit, null)

    override suspend fun loadLastQuote(assetIdentity: AssetIdentity) =
        StorageResult<AssetQuote>(null, StorageFailure.NOT_FOUND)

    override suspend fun saveLastQuote(quote: AssetQuote) = StorageResult(Unit, null)

    override fun close() = Unit
}

private object FixedMarketPriceSource : MarketPriceSource {
    override fun fetchMarketPrice(asset: Asset, completion: (NetworkResult<DecimalValue>) -> Unit) {
        completion(NetworkResult(DecimalValue("100"), null))
    }
}

private class ManualMarketPriceSource : MarketPriceSource {
    private lateinit var completion: (NetworkResult<DecimalValue>) -> Unit

    override fun fetchMarketPrice(asset: Asset, completion: (NetworkResult<DecimalValue>) -> Unit) {
        this.completion = completion
    }

    fun complete(price: DecimalValue) {
        completion(NetworkResult(price, null))
    }
}
