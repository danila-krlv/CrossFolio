package com.crossfolio.common.portfolio.storage

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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
): PortfolioStorage = RoomPortfolioStorage(
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build(),
)

internal class RoomPortfolioStorage(
    private val database: PortfolioDatabase,
) : PortfolioStorage {
    private val dao = database.portfolioDao()

    override suspend fun loadPositions(): StorageResult<List<PortfolioPosition>> {
        val records = try {
            dao.loadPositions()
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
            operations = position.operations.mapIndexed { index, operation ->
                operation.toEntity(identity, index)
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

    override suspend fun saveLastQuote(quote: AssetQuote): StorageResult<Unit> = try {
        dao.upsertQuote(quote.toEntity())
        success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        failure(StorageFailure.WRITE)
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

private fun PortfolioOperation.toEntity(identity: AssetIdentity, recordOrder: Int) = OperationEntity(
    searchPlatform = identity.searchPlatform.toStorageValue(),
    searchId = identity.searchId,
    operationId = id,
    recordOrder = recordOrder,
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

private fun PositionRecord.toModel(): PortfolioPosition {
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
