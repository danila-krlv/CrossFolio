package com.crossfolio.common.portfolio.storage

import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.crossfolio.common.analytics.PortfolioSnapshot
import androidx.sqlite.SQLiteDriver
import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.asset.AssetIdentity
import com.crossfolio.common.core.asset.SearchPlatform
import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.core.market.AssetQuote
import com.crossfolio.common.portfolio.model.AcquisitionPrice
import com.crossfolio.common.portfolio.model.AcquisitionPriceSource
import com.crossfolio.common.portfolio.model.PortfolioOperation
import com.crossfolio.common.portfolio.model.PortfolioOperationDirection
import com.crossfolio.common.portfolio.model.PortfolioPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers

internal fun createRoomPortfolioStorage(
    builder: RoomDatabase.Builder<PortfolioDatabase>,
    driver: SQLiteDriver,
): PortfolioStorage = RoomPortfolioStorage(
    builder
        .addMigrations(PORTFOLIO_MIGRATION_1_2)
        .setDriver(driver)
        .setQueryCoroutineContext(Dispatchers.Default)
        .build(),
)

internal val PORTFOLIO_MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `portfolio_snapshots` " +
            "(`observed_at_epoch_millis` INTEGER NOT NULL, `value_usd` TEXT NOT NULL, " +
            "PRIMARY KEY(`observed_at_epoch_millis`))")
    }
}

internal class RoomPortfolioStorage(
    private val database: PortfolioDatabase,
) : PortfolioStorage {
    private val dao = database.portfolioDao()

    override suspend fun loadPositions(): StorageResult<List<PortfolioPosition>> {
        val records = try {
            dao.loadPositionsAndRecordSnapshot()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return failure(StorageFailure.READ)
        }
        return try {
            success(records.map(PositionRecord::toModel))
        } catch (_: IllegalArgumentException) {
            failure(StorageFailure.INVALID_DATA)
        }
    }

    override suspend fun savePosition(position: PortfolioPosition): StorageResult<Unit> = try {
        val identity = position.asset.identity
        dao.savePosition(
            asset = position.asset.toEntity(),
            operations = position.operations.map { operation ->
                operation.toEntity(identity)
            },
            quote = position.latestQuote?.toEntity(),
        )
        success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        failure(StorageFailure.WRITE)
    }

    override suspend fun deletePosition(assetIdentity: AssetIdentity): StorageResult<Unit> = try {
        dao.deletePosition(assetIdentity.searchPlatform.toStorageValue(), assetIdentity.searchId)
        success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        failure(StorageFailure.WRITE)
    }

    override suspend fun loadLastQuote(assetIdentity: AssetIdentity): StorageResult<AssetQuote> {
        val entity = try {
            dao.loadQuote(assetIdentity.searchPlatform.toStorageValue(), assetIdentity.searchId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return failure(StorageFailure.READ)
        } ?: return failure(StorageFailure.NOT_FOUND)
        return try {
            success(entity.toModel())
        } catch (_: IllegalArgumentException) {
            failure(StorageFailure.INVALID_DATA)
        }
    }

    override suspend fun saveLastQuote(quote: AssetQuote): StorageResult<Unit> = saveLastQuotes(listOf(quote))

    override suspend fun saveLastQuotes(quotes: List<AssetQuote>): StorageResult<Unit> = try {
        dao.saveQuotes(quotes.map { it.toEntity() })
        success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        failure(StorageFailure.WRITE)
    }

    override suspend fun loadSnapshots(): StorageResult<List<PortfolioSnapshot>> = try {
        success(dao.loadSnapshots().map { PortfolioSnapshot(it.observedAtEpochMillis, DecimalValue(it.valueUsd)) })
    } catch (error: CancellationException) {
        throw error
    } catch (_: IllegalArgumentException) {
        failure(StorageFailure.INVALID_DATA)
    } catch (_: Exception) {
        failure(StorageFailure.READ)
    }

    override fun close() = database.close()
}

private fun Asset.toEntity() = AssetEntity(
    searchPlatform = searchPlatform.toStorageValue(),
    searchId = searchId,
    ticker = ticker,
    name = name,
    slug = slug,
    rank = rank,
)

private fun PortfolioOperation.toEntity(identity: AssetIdentity) = OperationEntity(
    searchPlatform = identity.searchPlatform.toStorageValue(),
    searchId = identity.searchId,
    operationId = id,
    recordOrder = 0,
    direction = direction.toStorageValue(),
    quantity = quantity.value,
    occurredAtEpochMillis = occurredAtEpochMillis,
    acquisitionPriceUsd = acquisitionPrice?.usd?.value,
    acquisitionPriceSource = acquisitionPrice?.source?.toStorageValue(),
    commissionUsd = commissionUsd.value,
)

private fun AssetQuote.toEntity() = QuoteEntity(
    searchPlatform = assetIdentity.searchPlatform.toStorageValue(),
    searchId = assetIdentity.searchId,
    priceUsd = priceUsd.value,
    receivedAtEpochMillis = receivedAtEpochMillis,
)

internal fun PositionRecord.toModel(): PortfolioPosition {
    val assetEntity = asset
    val asset = Asset(
        searchId = assetEntity.searchId,
        ticker = assetEntity.ticker,
        searchPlatform = assetEntity.searchPlatform.toSearchPlatform(),
        name = assetEntity.name,
        slug = assetEntity.slug,
        rank = assetEntity.rank,
    )
    return PortfolioPosition(
        asset = asset,
        operations = operations.map(OperationEntity::toModel),
        latestQuote = quote?.toModel(),
    )
}

private fun OperationEntity.toModel(): PortfolioOperation {
    val price = acquisitionPriceUsd?.let { usd ->
        AcquisitionPrice(
            usd = DecimalValue(usd),
            source = requireNotNull(acquisitionPriceSource).toAcquisitionPriceSource(),
        )
    }
    require((acquisitionPriceUsd == null) == (acquisitionPriceSource == null))
    return PortfolioOperation(
        id = operationId,
        direction = direction.toPortfolioOperationDirection(),
        quantity = DecimalValue(quantity),
        occurredAtEpochMillis = occurredAtEpochMillis,
        acquisitionPrice = price,
        commissionUsd = DecimalValue(commissionUsd),
    )
}

private fun QuoteEntity.toModel() = AssetQuote(
    assetIdentity = AssetIdentity(
        searchId = searchId,
        searchPlatform = searchPlatform.toSearchPlatform(),
    ),
    priceUsd = DecimalValue(priceUsd),
    receivedAtEpochMillis = receivedAtEpochMillis,
)

private fun <T : Any> success(value: T) = StorageResult(value, null)
private fun <T : Any> failure(failure: StorageFailure) = StorageResult<T>(null, failure)

private fun SearchPlatform.toStorageValue() = when (this) {
    SearchPlatform.COIN_MARKET_CAP -> "coin_market_cap"
}

private fun String.toSearchPlatform() = when (this) {
    "coin_market_cap" -> SearchPlatform.COIN_MARKET_CAP
    else -> throw IllegalArgumentException("Unknown search platform")
}

private fun PortfolioOperationDirection.toStorageValue() = when (this) {
    PortfolioOperationDirection.ADDITION -> "addition"
    PortfolioOperationDirection.REDUCTION -> "reduction"
}

private fun String.toPortfolioOperationDirection() = when (this) {
    "addition" -> PortfolioOperationDirection.ADDITION
    "reduction" -> PortfolioOperationDirection.REDUCTION
    else -> throw IllegalArgumentException("Unknown operation direction")
}

private fun AcquisitionPriceSource.toStorageValue() = when (this) {
    AcquisitionPriceSource.MANUAL -> "manual"
    AcquisitionPriceSource.MARKET -> "market"
}

private fun String.toAcquisitionPriceSource() = when (this) {
    "manual" -> AcquisitionPriceSource.MANUAL
    "market" -> AcquisitionPriceSource.MARKET
    else -> throw IllegalArgumentException("Unknown acquisition price source")
}
