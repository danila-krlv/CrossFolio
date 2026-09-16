package com.crossfolio.common.portfolio.storage

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

    /** Adds or updates the asset and operations present in this aggregate. Existing operations are not removed. */
    suspend fun savePosition(position: PortfolioPosition): StorageResult<Unit>

    /** Deletes the position together with all of its operations and its last quote. */
    suspend fun deletePosition(assetIdentity: AssetIdentity): StorageResult<Unit>

    suspend fun loadLastQuote(assetIdentity: AssetIdentity): StorageResult<AssetQuote>

    /** The position must already exist. */
    suspend fun saveLastQuote(quote: AssetQuote): StorageResult<Unit>

    fun close()
}
