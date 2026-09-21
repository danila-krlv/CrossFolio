package com.crossfolio.common.portfolio.model

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.core.market.AssetQuote

/** History is in recording order, not event-date order; past-date balances are not checked. */
class PortfolioPosition(
    val asset: Asset,
    operations: List<PortfolioOperation> = emptyList(),
    val latestQuote: AssetQuote? = null,
) {
    val operations: List<PortfolioOperation> = operations.toList()
    val quantity: DecimalValue

    fun record(
        operation: PortfolioOperation,
        nowEpochMillis: Long,
        rules: PortfolioOperationRules = PortfolioOperationRules(),
    ): PortfolioPosition {
        rules.validate(operation, quantity, nowEpochMillis)
        return PortfolioPosition(asset, operations + operation, latestQuote)
    }

    init {
        require(latestQuote == null || latestQuote.assetIdentity == asset.identity) {
            "Quote must belong to the position's coin"
        }
        require(this.operations.map { it.id }.distinct().size == this.operations.size) {
            "Operation IDs must be unique within a position"
        }
        quantity = this.operations.fold(DecimalValue.ZERO) { balance, operation ->
            when (operation.direction) {
                PortfolioOperationDirection.ADDITION -> balance.add(operation.quantity)
                PortfolioOperationDirection.REDUCTION -> balance.subtract(operation.quantity)
            }
        }
    }
}
