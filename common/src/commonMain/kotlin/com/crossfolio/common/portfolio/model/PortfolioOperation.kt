package com.crossfolio.common.portfolio.model

import com.crossfolio.common.core.decimal.DecimalValue

enum class PortfolioOperationDirection { ADDITION, REDUCTION }

/** Epoch milliseconds preserve the event instant across device time zone changes. */
data class PortfolioOperation(
    val id: String,
    val direction: PortfolioOperationDirection,
    val quantity: DecimalValue,
    val occurredAtEpochMillis: Long,
    val acquisitionPrice: AcquisitionPrice? = null,
    val commissionUsd: DecimalValue = DecimalValue.ZERO,
) {
    init {
        require(id.isNotBlank()) { "Operation ID must not be blank" }
        require(!quantity.isZero) { "Operation quantity must be positive" }
        require((direction == PortfolioOperationDirection.ADDITION) == (acquisitionPrice != null)) {
            "Only additions must have an acquisition price"
        }
    }
}
