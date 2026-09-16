package com.crossfolio.common.portfolio.overview

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.asset.AssetIdentity
import com.crossfolio.common.core.market.AssetQuote
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
}

private class PortfolioStorageFake(
    var positions: List<PortfolioPosition>,
) : PortfolioStorage {
    override suspend fun loadPositions() = StorageResult(positions, null)
    override suspend fun savePosition(position: PortfolioPosition) = StorageResult(Unit, null)
    override suspend fun deletePosition(assetIdentity: AssetIdentity) = StorageResult(Unit, null)
    override suspend fun loadLastQuote(assetIdentity: AssetIdentity) =
        StorageResult<AssetQuote>(null, StorageFailure.NOT_FOUND)
    override suspend fun saveLastQuote(quote: AssetQuote) = StorageResult(Unit, null)
    override fun close() = Unit
}
