package com.crossfolio.common.portfolio.storage

import androidx.sqlite.execSQL
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
    fun persistsExactMarketHistoryAcrossReopenAndDeletion() = runBlocking {
        val path = "${NSTemporaryDirectory()}crossfolio-history-${NSUUID().UUIDString}.db"
        var storage = createApplePortfolioStorage(path)
        try {
            assertEquals(emptyList(), storage.loadPositions().value)
            assertEquals(emptyList(), storage.loadSnapshots().value)
            val asset = Asset("1", "BTC")
            val operation = PortfolioOperation("a", PortfolioOperationDirection.ADDITION,
                DecimalValue("2"), 1000,
                AcquisitionPrice(DecimalValue("50"), AcquisitionPriceSource.MANUAL))
            assertNull(storage.savePosition(PortfolioPosition(asset, listOf(operation))).failure)
            assertEquals(emptyList(), storage.loadSnapshots().value)
            assertNull(storage.saveLastQuote(AssetQuote(asset.identity, DecimalValue("0.006"), 2000)).failure)
            assertEquals(DecimalValue("0.012"), storage.loadSnapshots().value!!.last().valueUsd)
            storage.close()
            storage = createApplePortfolioStorage(path)
            assertEquals(DecimalValue("0.012"), storage.loadSnapshots().value!!.last().valueUsd)
            assertNull(storage.deletePosition(asset.identity).failure)
            assertEquals(DecimalValue.ZERO, storage.loadSnapshots().value!!.last().valueUsd)
        } finally {
            storage.close()
            listOf(path, "$path-wal", "$path-shm").forEach {
                NSFileManager.defaultManager.removeItemAtPath(it, null)
            }
        }
    }

    @Test
    fun migratesVersionOneWithoutLosingPortfolio() = runBlocking {
        val path = "${NSTemporaryDirectory()}crossfolio-migration-${NSUUID().UUIDString}.db"
        // SQL from the committed version-one schema; opening through Room exercises its validation.
        val connection = portfolioSQLiteDriver().open(path)
        try {
            connection.execSQL("CREATE TABLE IF NOT EXISTS `assets` (`search_platform` TEXT NOT NULL, `search_id` TEXT NOT NULL, `ticker` TEXT NOT NULL, `name` TEXT NOT NULL, `slug` TEXT NOT NULL, `rank` INTEGER, PRIMARY KEY(`search_platform`, `search_id`))")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `operations` (`search_platform` TEXT NOT NULL, `search_id` TEXT NOT NULL, `operation_id` TEXT NOT NULL, `record_order` INTEGER NOT NULL, `direction` TEXT NOT NULL, `quantity` TEXT NOT NULL, `occurred_at_epoch_millis` INTEGER NOT NULL, `acquisition_price_usd` TEXT, `acquisition_price_source` TEXT, `commission_usd` TEXT NOT NULL, PRIMARY KEY(`search_platform`, `search_id`, `operation_id`), FOREIGN KEY(`search_platform`, `search_id`) REFERENCES `assets`(`search_platform`, `search_id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `last_quotes` (`search_platform` TEXT NOT NULL, `search_id` TEXT NOT NULL, `price_usd` TEXT NOT NULL, `received_at_epoch_millis` INTEGER NOT NULL, PRIMARY KEY(`search_platform`, `search_id`), FOREIGN KEY(`search_platform`, `search_id`) REFERENCES `assets`(`search_platform`, `search_id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_operations_search_platform_search_id_record_order` ON `operations` (`search_platform`, `search_id`, `record_order`)")
            connection.execSQL("INSERT INTO assets VALUES ('coin_market_cap', '1', 'BTC', 'Bitcoin', 'bitcoin', 1)")
            connection.execSQL("INSERT INTO operations VALUES ('coin_market_cap', '1', 'old', 0, 'addition', '2', 1000, '5', 'manual', '0')")
            connection.execSQL("INSERT INTO last_quotes VALUES ('coin_market_cap', '1', '3.005', 2000)")
            connection.execSQL("PRAGMA user_version = 1")
        } finally {
            connection.close()
        }
        val storage = createApplePortfolioStorage(path)
        try {
            val position = assertNotNull(storage.loadPositions().value).single()
            assertEquals("BTC", position.asset.ticker)
            assertEquals("old", position.operations.single().id)
            assertEquals(DecimalValue("2"), position.quantity)
            assertEquals(DecimalValue("3.005"), position.latestQuote?.priceUsd)
            assertEquals(DecimalValue("6.01"), storage.loadSnapshots().value!!.last().valueUsd)
        } finally {
            storage.close()
            listOf(path, "$path-wal", "$path-shm").forEach {
                NSFileManager.defaultManager.removeItemAtPath(it, null)
            }
        }
    }

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

            val duplicateAsset = Asset("1", "CHANGED", name = "Changed")
            val appended = PortfolioOperation(
                id = "appended",
                direction = PortfolioOperationDirection.ADDITION,
                quantity = DecimalValue("1"),
                occurredAtEpochMillis = 2_500,
                acquisitionPrice = AcquisitionPrice(
                    DecimalValue("55000"),
                    AcquisitionPriceSource.MARKET,
                ),
            )
            val appendedPosition = PortfolioPosition(duplicateAsset, listOf(appended), initialQuote)
            assertNull(storage.savePosition(appendedPosition).failure)
            assertNull(storage.savePosition(appendedPosition).failure)

            val combined = assertNotNull(storage.loadPositions().value).single()
            assertEquals(asset, combined.asset)
            assertEquals(listOf(addition, reduction, appended), combined.operations)
            assertEquals(DecimalValue("2.5"), combined.quantity)

            val updatedQuote = AssetQuote(asset.identity, DecimalValue("61000"), 4_000)
            assertNull(storage.saveLastQuote(updatedQuote).failure)
            assertNull(storage.saveLastQuote(initialQuote).failure)
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
