package com.crossfolio.common.portfolio.storage

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "assets",
    primaryKeys = ["search_platform", "search_id"],
)
internal data class AssetEntity(
    @ColumnInfo(name = "search_platform") val searchPlatform: String,
    @ColumnInfo(name = "search_id") val searchId: String,
    val ticker: String,
    val name: String,
    val slug: String,
    val rank: Int?,
)

@Entity(
    tableName = "operations",
    primaryKeys = ["search_platform", "search_id", "operation_id"],
    foreignKeys = [ForeignKey(
        entity = AssetEntity::class,
        parentColumns = ["search_platform", "search_id"],
        childColumns = ["search_platform", "search_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["search_platform", "search_id", "record_order"], unique = true)],
)
internal data class OperationEntity(
    @ColumnInfo(name = "search_platform") val searchPlatform: String,
    @ColumnInfo(name = "search_id") val searchId: String,
    @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "record_order") val recordOrder: Int,
    val direction: String,
    val quantity: String,
    @ColumnInfo(name = "occurred_at_epoch_millis") val occurredAtEpochMillis: Long,
    @ColumnInfo(name = "acquisition_price_usd") val acquisitionPriceUsd: String?,
    @ColumnInfo(name = "acquisition_price_source") val acquisitionPriceSource: String?,
    @ColumnInfo(name = "commission_usd") val commissionUsd: String,
)

@Entity(
    tableName = "last_quotes",
    primaryKeys = ["search_platform", "search_id"],
    foreignKeys = [ForeignKey(
        entity = AssetEntity::class,
        parentColumns = ["search_platform", "search_id"],
        childColumns = ["search_platform", "search_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
internal data class QuoteEntity(
    @ColumnInfo(name = "search_platform") val searchPlatform: String,
    @ColumnInfo(name = "search_id") val searchId: String,
    @ColumnInfo(name = "price_usd") val priceUsd: String,
    @ColumnInfo(name = "received_at_epoch_millis") val receivedAtEpochMillis: Long,
)

@Entity(tableName = "portfolio_snapshots", primaryKeys = ["observed_at_epoch_millis"])
internal data class SnapshotEntity(
    @ColumnInfo(name = "observed_at_epoch_millis") val observedAtEpochMillis: Long,
    @ColumnInfo(name = "value_usd") val valueUsd: String,
)

internal data class PositionRecord(
    val asset: AssetEntity,
    val operations: List<OperationEntity>,
    val quote: QuoteEntity?,
)
