package com.crossfolio.common.portfolio.storage

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
    @Query("SELECT * FROM assets ORDER BY ticker, search_platform, search_id")
    protected abstract suspend fun loadAssets(): List<AssetEntity>

    @Query(
        "SELECT * FROM operations WHERE search_platform = :platform AND search_id = :searchId " +
            "ORDER BY record_order",
    )
    protected abstract suspend fun loadOperations(platform: String, searchId: String): List<OperationEntity>

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
    abstract suspend fun deletePosition(platform: String, searchId: String)

    @Transaction
    open suspend fun loadPositions(): List<PositionRecord> = loadAssets().map { asset ->
        PositionRecord(
            asset = asset,
            operations = loadOperations(asset.searchPlatform, asset.searchId),
            quote = loadQuote(asset.searchPlatform, asset.searchId),
        )
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
        if (quote != null) saveQuote(quote)
    }

    @Transaction
    open suspend fun saveQuote(quote: QuoteEntity) {
        val stored = loadQuote(quote.searchPlatform, quote.searchId)
        if (stored == null || quote.receivedAtEpochMillis >= stored.receivedAtEpochMillis) {
            upsertQuote(quote)
        }
    }
}

@Database(
    entities = [AssetEntity::class, OperationEntity::class, QuoteEntity::class],
    version = 1,
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
