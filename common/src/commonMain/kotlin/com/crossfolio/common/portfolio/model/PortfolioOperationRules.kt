package com.crossfolio.common.portfolio.model

import com.crossfolio.common.core.decimal.DecimalValue

/** Operation constraints, independent of form parsing and UI messages. */
class PortfolioOperationRules(
    val quantityFractionDigits: Int = 8,
    val manualPriceFractionDigits: Int = 2,
    val commissionFractionDigits: Int = 2,
    val maximumInputValue: DecimalValue = DecimalValue("10000000"),
) {
    init {
        require(quantityFractionDigits >= 0 && manualPriceFractionDigits >= 0 && commissionFractionDigits >= 0)
        require(!maximumInputValue.isZero)
    }

    fun validate(operation: PortfolioOperation, currentQuantity: DecimalValue, nowEpochMillis: Long) {
        validateInput(operation.quantity, quantityFractionDigits)
        validateInput(operation.commissionUsd, commissionFractionDigits)
        operation.acquisitionPrice?.let { price ->
            if (price.source == AcquisitionPriceSource.MANUAL) {
                validateInput(price.usd, manualPriceFractionDigits)
            }
        }
        require(operation.occurredAtEpochMillis <= nowEpochMillis) { "Future operations are not allowed" }
        if (operation.direction == PortfolioOperationDirection.REDUCTION) {
            require(operation.quantity <= currentQuantity) { "Reduction exceeds the current balance" }
        }
    }

    private fun validateInput(value: DecimalValue, fractionDigits: Int) {
        require(value.fractionDigits <= fractionDigits) { "Too many fractional digits" }
        require(value <= maximumInputValue) { "Input exceeds the maximum value" }
    }
}
