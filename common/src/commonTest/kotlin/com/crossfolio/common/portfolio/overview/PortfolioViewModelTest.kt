package com.crossfolio.common.portfolio.overview

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.asset.AssetIdentity
import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.core.market.AssetQuote
import com.crossfolio.common.core.market.MarketPriceSource
import com.crossfolio.common.core.network.NetworkResult
import com.crossfolio.common.core.network.NetworkFailure
import com.crossfolio.common.portfolio.model.AcquisitionPrice
import com.crossfolio.common.portfolio.model.AcquisitionPriceSource
import com.crossfolio.common.portfolio.model.PortfolioOperation
import com.crossfolio.common.portfolio.model.PortfolioOperationDirection
import com.crossfolio.common.portfolio.model.PortfolioPosition
import com.crossfolio.common.portfolio.storage.PortfolioStorage
import com.crossfolio.common.portfolio.storage.StorageFailure
import com.crossfolio.common.portfolio.storage.StorageResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PortfolioViewModelTest {
    @Test
    fun refreshesStaleAndMissingQuotesWithoutDuplicateRequests() {
        var now = 600_000L
        val storage = PortfolioStorageFake(listOf(position("1", "BTC", "2", "10"), position("2", "ETH", "3", null)))
        val source = PriceSourceFake()
        val model = PortfolioViewModel({}, storage, marketPriceSource = source, nowEpochMillis = { now })
        assertEquals(listOf("2"), source.requests.map { it.first.searchId })
        now = 600_001
        model.loadPositions()
        model.loadPositions()
        assertEquals(listOf("2", "1"), source.requests.map { it.first.searchId })
        source.requests[0].second(NetworkResult(DecimalValue("20"), null))
        source.requests[1].second(NetworkResult(DecimalValue("30"), null))
        assertEquals(DecimalValue("120"), model.state.value.totalValueUsd)
        assertEquals(2, storage.savedQuotes.size)
        model.loadPositions()
        assertEquals(2, source.requests.size)
    }

    @Test
    fun retriesOnlyOnOpeningAfterFailureCooldownAndKeepsCachedValues() {
        var now = 600_001L
        val storage = PortfolioStorageFake(listOf(position("1", "BTC", "2", "10")))
        val source = PriceSourceFake()
        val model = PortfolioViewModel({}, storage, marketPriceSource = source, nowEpochMillis = { now })
        now += 30_000
        source.requests.single().second(NetworkResult(null, "offline", NetworkFailure.TRANSPORT))
        assertEquals(DecimalValue("20"), model.state.value.totalValueUsd)
        assertEquals(0, storage.savedQuotes.size)
        now += 59_999
        model.loadPositions()
        assertEquals(1, source.requests.size)
        now++
        assertEquals(1, source.requests.size)
        model.loadPositions()
        assertEquals(2, source.requests.size)
    }

    @Test
    fun waitsForValidKeyAndIgnoresInvalidatedResponses() {
        var enabled = false
        val storage = PortfolioStorageFake(listOf(position("1", "BTC", "2", null)))
        val source = PriceSourceFake()
        val model = PortfolioViewModel({}, storage, marketPriceSource = source, canRefreshPrices = { enabled })
        assertEquals(0, source.requests.size)
        enabled = true
        model.loadPositions()
        model.invalidatePriceRequests()
        source.requests.single().second(NetworkResult(DecimalValue("30"), null))
        assertEquals(0, storage.savedQuotes.size)
        assertEquals(DecimalValue("2"), model.state.value.totalValueUsd)
        model.loadPositions()
        assertEquals(2, source.requests.size)
    }

    @Test
    fun loadsPositionsOnCreationAndRefresh() {
        val first = PortfolioPosition(Asset("1", "BTC"))
        val second = PortfolioPosition(Asset("2", "ETH"))
        val storage = PortfolioStorageFake(listOf(first))
        val model = PortfolioViewModel({}, storage)

        assertEquals(listOf(first), model.state.value.positions)
        assertFalse(model.state.value.isLoading)
        assertNull(model.state.value.storageFailure)

        storage.positions = listOf(first, second)
        model.loadPositions()

        assertEquals(listOf(first, second), model.state.value.positions)
    }

    @Test
    fun calculatesPositionValuesAndTotalWithUsdFallback() {
        val bitcoin = position("1", "BTC", "1.23456789", "100.005")
        val fallback = position("2", "NEW", "2", null)
        val storage = PortfolioStorageFake(listOf(bitcoin, fallback))

        val state = PortfolioViewModel({}, storage).state.value

        assertEquals(DecimalValue("123.46296183945"), state.rows[0].valueUsd)
        assertEquals("123.46", state.rows[0].valueUsdText)
        assertEquals(DecimalValue("2"), state.rows[1].valueUsd)
        assertEquals("2.00", state.rows[1].valueUsdText)
        assertEquals(DecimalValue("125.46296183945"), state.totalValueUsd)
        assertEquals("125.46", state.totalValueUsdText)
    }

    @Test
    fun roundsUsdHalfUp() {
        val state = PortfolioViewModel(
            onAssetSearchRequested = {},
            portfolioStorage = PortfolioStorageFake(listOf(position("1", "TEST", "1", "1.005"))),
        ).state.value

        assertEquals("1.01", state.rows.single().valueUsdText)
        assertEquals("1.01", state.totalValueUsdText)
    }

    private fun position(id: String, ticker: String, quantity: String, quote: String?): PortfolioPosition {
        val asset = Asset(id, ticker)
        val operation = PortfolioOperation(
            id = "operation-$id",
            direction = PortfolioOperationDirection.ADDITION,
            quantity = DecimalValue.parse(quantity),
            occurredAtEpochMillis = 1,
            acquisitionPrice = AcquisitionPrice(DecimalValue("1"), AcquisitionPriceSource.MANUAL),
        )
        return PortfolioPosition(
            asset = asset,
            operations = listOf(operation),
            latestQuote = quote?.let { AssetQuote(asset.identity, DecimalValue.parse(it), 1) },
        )
    }
}

private class PortfolioStorageFake(
    var positions: List<PortfolioPosition>,
) : PortfolioStorage {
    val savedQuotes = mutableListOf<AssetQuote>()
    override suspend fun loadPositions() = StorageResult(positions, null)
    override suspend fun savePosition(position: PortfolioPosition) = StorageResult(Unit, null)
    override suspend fun deletePosition(assetIdentity: AssetIdentity) = StorageResult(Unit, null)
    override suspend fun loadLastQuote(assetIdentity: AssetIdentity) =
        StorageResult<AssetQuote>(null, StorageFailure.NOT_FOUND)
    override suspend fun saveLastQuote(quote: AssetQuote): StorageResult<Unit> {
        savedQuotes.add(quote)
        positions = positions.map {
            if (it.asset.identity == quote.assetIdentity) PortfolioPosition(it.asset, it.operations, quote) else it
        }
        return StorageResult(Unit, null)
    }
    override fun close() = Unit
}

private class PriceSourceFake : MarketPriceSource {
    val requests = mutableListOf<Pair<Asset, (NetworkResult<DecimalValue>) -> Unit>>()
    override fun fetchMarketPrice(asset: Asset, completion: (NetworkResult<DecimalValue>) -> Unit) {
        requests.add(asset to completion)
    }
}
