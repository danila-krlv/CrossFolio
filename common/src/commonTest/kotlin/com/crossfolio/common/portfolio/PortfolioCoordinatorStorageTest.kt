package com.crossfolio.common.portfolio

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.asset.AssetCatalog
import com.crossfolio.common.core.asset.AssetIdentity
import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.core.market.AssetQuote
import com.crossfolio.common.core.market.MarketPriceSource
import com.crossfolio.common.core.network.NetworkResult
import com.crossfolio.common.portfolio.model.PortfolioPosition
import com.crossfolio.common.portfolio.storage.PortfolioStorage
import com.crossfolio.common.portfolio.storage.StorageFailure
import com.crossfolio.common.portfolio.storage.StorageResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PortfolioCoordinatorStorageTest {
    @Test
    fun successfulSaveReloadsPortfolioAndReturnsToRoot() {
        val asset = Asset("1", "BTC")
        val storage = CoordinatorStorageFake()
        val coordinator = PortfolioCoordinator(
            assetCatalog = object : AssetCatalog {
                override fun fetchMap(completion: (NetworkResult<List<Asset>>) -> Unit) {
                    completion(NetworkResult(listOf(asset), null))
                }
            },
            marketPriceSource = object : MarketPriceSource {
                override fun fetchMarketPrice(
                    asset: Asset,
                    completion: (NetworkResult<DecimalValue>) -> Unit,
                ) {
                    completion(NetworkResult(DecimalValue("100"), null))
                }
            },
            portfolioStorage = storage,
        )
        coordinator.setSearchEnabled(true)
        coordinator.openAssetSearch()
        coordinator.assetSearchViewModel.selectAsset(asset)
        val edit = requireNotNull(coordinator.editViewModel)
        edit.setQuantity("1")

        edit.save()

        assertEquals(PortfolioRoute.PORTFOLIO, coordinator.state.value.currentRoute)
        assertNull(coordinator.editViewModel)
        assertEquals(storage.positions, coordinator.portfolioViewModel.state.value.positions)
    }
}

private class CoordinatorStorageFake : PortfolioStorage {
    var positions: List<PortfolioPosition> = emptyList()

    override suspend fun loadPositions() = StorageResult(positions, null)

    override suspend fun savePosition(position: PortfolioPosition): StorageResult<Unit> {
        positions = listOf(position)
        return StorageResult(Unit, null)
    }

    override suspend fun deletePosition(assetIdentity: AssetIdentity) = StorageResult(Unit, null)

    override suspend fun loadLastQuote(assetIdentity: AssetIdentity) =
        StorageResult<AssetQuote>(null, StorageFailure.NOT_FOUND)

    override suspend fun saveLastQuote(quote: AssetQuote) = StorageResult(Unit, null)

    override suspend fun loadSnapshots() = com.crossfolio.common.portfolio.storage.StorageResult(
        emptyList<com.crossfolio.common.analytics.PortfolioSnapshot>(), null,
    )

    override fun close() = Unit
}
