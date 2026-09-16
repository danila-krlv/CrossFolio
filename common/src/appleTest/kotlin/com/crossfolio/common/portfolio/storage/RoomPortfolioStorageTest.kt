package com.crossfolio.common.portfolio.storage

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.core.market.AssetQuote
import com.crossfolio.common.portfolio.model.AcquisitionPrice
import com.crossfolio.common.portfolio.model.AcquisitionPriceSource
import com.crossfolio.common.portfolio.model.PortfolioOperation
import com.crossfolio.common.portfolio.model.PortfolioOperationDirection
import com.crossfolio.common.portfolio.model.PortfolioPosition
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalForeignApi::class)
class RoomPortfolioStorageTest {
    @Test
    fun persistsPositionQuoteAndCascadeDeletion() = runBlocking {
        val databasePath = "${NSTemporaryDirectory()}crossfolio-${NSUUID().UUIDString}.db"
        val storage = createApplePortfolioStorage(databasePath)
        try {
            val asset = Asset("1", "BTC", name = "Bitcoin", slug = "bitcoin", rank = 1)
            val addition = PortfolioOperation(
                id = "addition",
                direction = PortfolioOperationDirection.ADDITION,
                quantity = DecimalValue("2"),
                occurredAtEpochMillis = 1_000,
                acquisitionPrice = AcquisitionPrice(
                    DecimalValue("50000.25"),
                    AcquisitionPriceSource.MANUAL,
                ),
                commissionUsd = DecimalValue("1.5"),
            )
            val reduction = PortfolioOperation(
                id = "reduction",
                direction = PortfolioOperationDirection.REDUCTION,
                quantity = DecimalValue("0.5"),
                occurredAtEpochMillis = 2_000,
                commissionUsd = DecimalValue("0.25"),
            )
            val initialQuote = AssetQuote(asset.identity, DecimalValue("60000.125"), 3_000)
            val position = PortfolioPosition(asset, listOf(addition, reduction), initialQuote)

            assertNull(storage.savePosition(position).failure)

            val loaded = assertNotNull(storage.loadPositions().value).single()
            assertEquals(asset, loaded.asset)
            assertEquals(listOf(addition, reduction), loaded.operations)
            assertEquals(DecimalValue("1.5"), loaded.quantity)
            assertEquals(initialQuote, loaded.latestQuote)

            val updatedQuote = AssetQuote(asset.identity, DecimalValue("61000"), 4_000)
            assertNull(storage.saveLastQuote(updatedQuote).failure)
            assertEquals(updatedQuote, storage.loadLastQuote(asset.identity).value)

            assertNull(storage.deletePosition(asset.identity).failure)
            assertEquals(emptyList(), storage.loadPositions().value)
            assertEquals(StorageFailure.NOT_FOUND, storage.loadLastQuote(asset.identity).failure)
        } finally {
            storage.close()
            listOf(databasePath, "$databasePath-wal", "$databasePath-shm").forEach { path ->
                NSFileManager.defaultManager.removeItemAtPath(path, null)
            }
        }
    }
}
