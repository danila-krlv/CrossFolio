package com.crossfolio.common.analytics

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.asset.AssetIdentity
import com.crossfolio.common.portfolio.storage.PortfolioStorage
import com.crossfolio.common.portfolio.storage.StorageResult
import com.crossfolio.common.portfolio.storage.StorageFailure
import com.crossfolio.common.portfolio.overview.PortfolioViewModel
import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.core.market.AssetQuote
import com.crossfolio.common.portfolio.model.AcquisitionPrice
import com.crossfolio.common.portfolio.model.AcquisitionPriceSource
import com.crossfolio.common.portfolio.model.PortfolioOperation
import com.crossfolio.common.portfolio.model.PortfolioOperationDirection
import com.crossfolio.common.portfolio.model.PortfolioPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnalyticsTest {
    @Test
    fun usesMarketValuesNotQuantitiesOrAcquisitionPrices() {
        val positions = listOf(position("1", "2", "30"), position("2", "4", "10"))
        assertEquals(DecimalValue("100"), marketValue(positions))
        assertEquals(listOf("60%", "40%"), assetShares(positions).map { it.percentText })
        assertEquals(1000, assetShares(positions).sumOf { it.tenthsPercent })
    }

    @Test
    fun roundsSharesToOneHundredAndKeepsIdenticalTickersSeparate() {
        val positions = (1..3).map { position(it.toString(), "1", "0.000000001") }
        assertEquals(DecimalValue("0.000000003"), marketValue(positions))
        val shares = assetShares(positions)
        assertEquals(listOf("1", "2", "3"), shares.map { it.asset.searchId })
        assertEquals(listOf("33.4%", "33.3%", "33.3%"), shares.map { it.percentText })
        assertEquals("<\$0.01", PortfolioSnapshot(1, marketValue(positions)!!).valueUsdText)
        assertEquals("\$1.01", PortfolioSnapshot(1, DecimalValue("1.005")).valueUsdText)
    }

    @Test
    fun doesNotInventQuotesOrIncludeZeroPositions() {
        val missing = position("1", "1", null)
        assertNull(marketValue(listOf(missing)))
        assertEquals(emptyList(), assetShares(listOf(missing)))
        val zero = PortfolioPosition(Asset("2", "SAME"))
        assertEquals(DecimalValue.ZERO, marketValue(listOf(zero)))
        assertEquals(emptyList(), assetShares(listOf(zero)))
        assertEquals("100%", assetShares(listOf(zero, position("3", "1", "2"))).single().percentText)
        assertEquals(emptyList(), assetShares(emptyList()))
    }

    @Test
    fun retainsTinySharesAndAddsBeforeRounding() {
        val shares = assetShares(listOf(position("1", "1", "100"), position("2", "1", "0.000001")))
        assertEquals("<0.1%", shares.last().percentText)
        assertEquals(DecimalValue("0.012"), marketValue(listOf(position("1", "1", "0.006"), position("2", "1", "0.006"))))
    }

    @Test
    fun viewModelUpdatesSharesAndRetriesHistoryFailure() {
        val storage = AnalyticsStorageFake()
        val portfolio = PortfolioViewModel({}, storage)
        val model = AnalyticsViewModel(portfolio, storage)
        storage.positions = listOf(position("1", "2", "30"), position("2", "4", "10"))
        storage.history = listOf(PortfolioSnapshot(2000, DecimalValue("100")))
        model.refresh()
        assertEquals(listOf("60%", "40%"), model.state.value.shares.map { it.percentText })
        assertEquals(storage.history, model.state.value.history)
        storage.failHistory = true
        model.refresh()
        kotlin.test.assertNotNull(model.state.value.errorMessage)
        assertEquals(storage.history, model.state.value.history)
        storage.failHistory = false
        model.refresh()
        assertNull(model.state.value.errorMessage)
    }

    private fun position(id: String, quantity: String, price: String?): PortfolioPosition {
        val asset = Asset(id, "SAME")
        return PortfolioPosition(asset, listOf(PortfolioOperation(
            id, PortfolioOperationDirection.ADDITION, DecimalValue(quantity), 1000,
            AcquisitionPrice(DecimalValue("999"), AcquisitionPriceSource.MANUAL),
        )), price?.let { AssetQuote(asset.identity, DecimalValue(it), 2000) })
    }
}

private class AnalyticsStorageFake : PortfolioStorage {
    var positions = emptyList<PortfolioPosition>()
    var history = emptyList<PortfolioSnapshot>()
    var failHistory = false
    override suspend fun loadPositions() = StorageResult(positions, null)
    override suspend fun loadSnapshots() = StorageResult(
        if (failHistory) null else history,
        if (failHistory) StorageFailure.READ else null,
    )
    override suspend fun savePosition(position: PortfolioPosition) = StorageResult(Unit, null)
    override suspend fun deletePosition(assetIdentity: AssetIdentity) = StorageResult(Unit, null)
    override suspend fun saveLastQuote(quote: AssetQuote) = StorageResult(Unit, null)
    override suspend fun loadLastQuote(assetIdentity: AssetIdentity) =
        StorageResult<AssetQuote>(null, StorageFailure.NOT_FOUND)
    override fun close() {}
}
