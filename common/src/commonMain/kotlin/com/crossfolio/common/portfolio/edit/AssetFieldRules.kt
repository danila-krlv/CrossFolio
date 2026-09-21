package com.crossfolio.common.portfolio.edit

import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.portfolio.model.PortfolioOperationRules

/** Parses form fields using the same limits as operation validation. */
class AssetFieldRules(val operationRules: PortfolioOperationRules = PortfolioOperationRules()) {
    fun parseQuantity(text: String): DecimalValue = parseInput(text, operationRules.quantityFractionDigits, false)

    fun parseManualPrice(text: String): DecimalValue = parseInput(text, operationRules.manualPriceFractionDigits, false)

    fun parseCommission(text: String): DecimalValue =
        if (text.isBlank()) DecimalValue.ZERO else parseInput(text, operationRules.commissionFractionDigits, true)

    private fun parseInput(text: String, fractionDigits: Int, allowZero: Boolean): DecimalValue {
        val normalized = text.trim().replace(',', '.')
        require(normalized.matches(Regex("[0-9]+(?:\\.[0-9]+)?"))) { "Введите корректное число" }
        require(normalized.substringAfter('.', "").length <= fractionDigits) {
            "Допустимо знаков после запятой: $fractionDigits"
        }
        val value = DecimalValue.parse(normalized)
        require(allowZero || !value.isZero) { "Значение должно быть больше нуля" }
        require(value <= operationRules.maximumInputValue) { "Максимум: ${operationRules.maximumInputValue.value}" }
        return value
    }
}
