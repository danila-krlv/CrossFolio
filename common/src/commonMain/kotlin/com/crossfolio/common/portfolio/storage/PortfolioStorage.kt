package com.crossfolio.common.portfolio.storage

import com.crossfolio.common.analytics.PortfolioSnapshot
import com.crossfolio.common.core.asset.AssetIdentity
import com.crossfolio.common.core.market.AssetQuote
import com.crossfolio.common.portfolio.model.PortfolioPosition

enum class StorageFailure {
    READ,
    WRITE,
    INVALID_DATA,
    NOT_FOUND,
}

class StorageResult<T : Any>(
    val value: T?,
    val failure: StorageFailure?,
) {
    init {
        require((value != null) != (failure != null)) {
            "Storage result must contain either a value or a failure"
        }
    }
}

interface PortfolioStorage {
    suspend fun loadPositions(): StorageResult<List<PortfolioPosition>>

    /** Creates a position or appends new operation IDs without replacing the stored asset or history. */
    suspend fun savePosition(position: PortfolioPosition): StorageResult<Unit>

    /** Deletes the position together with all of its operations and its last quote. */
    suspend fun deletePosition(assetIdentity: AssetIdentity): StorageResult<Unit>

    suspend fun loadLastQuote(assetIdentity: AssetIdentity): StorageResult<AssetQuote>

    /** The position must already exist. */
    suspend fun saveLastQuote(quote: AssetQuote): StorageResult<Unit>

    /** Saves a refresh batch atomically and records a single portfolio snapshot. */
    suspend fun saveLastQuotes(quotes: List<AssetQuote>): StorageResult<Unit>

    suspend fun loadSnapshots(): StorageResult<List<PortfolioSnapshot>>

    fun close()
}
