package com.crossfolio.common.portfolio.storage

import kotlin.time.Clock
import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Transaction
import androidx.room3.Upsert

@Dao
internal abstract class PortfolioDao {
    @Query("SELECT * FROM portfolio_snapshots ORDER BY observed_at_epoch_millis")
    abstract suspend fun loadSnapshots(): List<SnapshotEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM portfolio_snapshots)")
    protected abstract suspend fun hasSnapshots(): Boolean

    @Upsert
    protected abstract suspend fun upsertSnapshot(snapshot: SnapshotEntity)

    private suspend fun recordSnapshot(records: List<PositionRecord>) {
        val positions = records.map { it.toModel() }
        val total = com.crossfolio.common.analytics.marketValue(positions) ?: return
        // Empty initial portfolios need no artificial zero before their first investment.
        if (positions.isEmpty() && !hasSnapshots()) return
        upsertSnapshot(SnapshotEntity(Clock.System.now().toEpochMilliseconds(), total.value))
    }

    @Transaction
    open suspend fun loadPositionsAndRecordSnapshot(): List<PositionRecord> {
        val positions = loadPositions()
        recordSnapshot(positions)
        return positions
    }

    @Query("SELECT * FROM assets ORDER BY ticker, search_platform, search_id")
    protected abstract suspend fun loadAssets(): List<AssetEntity>

    @Query("SELECT * FROM operations ORDER BY search_platform, search_id, record_order")
    protected abstract suspend fun loadAllOperations(): List<OperationEntity>

    @Query("SELECT * FROM last_quotes")
    protected abstract suspend fun loadAllQuotes(): List<QuoteEntity>

    @Query("SELECT * FROM last_quotes WHERE search_platform = :platform AND search_id = :searchId")
    abstract suspend fun loadQuote(platform: String, searchId: String): QuoteEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertAsset(asset: AssetEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertOperation(operation: OperationEntity): Long

    @Query(
        "SELECT COALESCE(MAX(record_order), -1) FROM operations " +
            "WHERE search_platform = :platform AND search_id = :searchId",
    )
    protected abstract suspend fun lastOperationOrder(platform: String, searchId: String): Int

    @Upsert
    protected abstract suspend fun upsertQuote(quote: QuoteEntity)

    @Query("DELETE FROM assets WHERE search_platform = :platform AND search_id = :searchId")
    protected abstract suspend fun deleteAsset(platform: String, searchId: String)

    @Transaction
    open suspend fun deletePosition(platform: String, searchId: String) {
        deleteAsset(platform, searchId)
        recordSnapshot(loadPositions())
    }

    @Transaction
    open suspend fun loadPositions(): List<PositionRecord> {
        val assets = loadAssets()
        val operations = loadAllOperations().groupBy { it.searchPlatform to it.searchId }
        val quotes = loadAllQuotes().associateBy { it.searchPlatform to it.searchId }
        return assets.map { asset ->
            val identity = asset.searchPlatform to asset.searchId
            PositionRecord(asset, operations[identity].orEmpty(), quotes[identity])
        }
    }

    @Transaction
    open suspend fun savePosition(
        asset: AssetEntity,
        operations: List<OperationEntity>,
        quote: QuoteEntity?,
    ) {
        insertAsset(asset)
        var nextOrder = lastOperationOrder(asset.searchPlatform, asset.searchId) + 1
        operations.forEach { operation ->
            if (insertOperation(operation.copy(recordOrder = nextOrder)) != -1L) nextOrder++
        }
        if (quote != null) saveQuotes(listOf(quote)) else recordSnapshot(loadPositions())
    }

    @Transaction
    open suspend fun saveQuotes(quotes: List<QuoteEntity>) {
        if (quotes.isEmpty()) return
        for (quote in quotes) {
            val stored = loadQuote(quote.searchPlatform, quote.searchId)
            if (stored == null || quote.receivedAtEpochMillis >= stored.receivedAtEpochMillis) {
                upsertQuote(quote)
            }
        }
        recordSnapshot(loadPositions())
    }

}

@Database(
    entities = [AssetEntity::class, OperationEntity::class, QuoteEntity::class, SnapshotEntity::class],
    version = 2,
    exportSchema = true,
)
@ConstructedBy(PortfolioDatabaseConstructor::class)
internal abstract class PortfolioDatabase : RoomDatabase() {
    abstract fun portfolioDao(): PortfolioDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
internal expect object PortfolioDatabaseConstructor : RoomDatabaseConstructor<PortfolioDatabase> {
    override fun initialize(): PortfolioDatabase
}
