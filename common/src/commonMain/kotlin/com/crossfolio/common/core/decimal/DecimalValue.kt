package com.crossfolio.common.core.decimal

/** Exact, non-negative decimal. Canonical text is suitable for future persistence. */
data class DecimalValue(val value: String) : Comparable<DecimalValue> {
    init {
        require(value.matches(Regex("(?:0|[1-9][0-9]*)(?:\\.[0-9]*[1-9])?"))) {
            "Expected a canonical non-negative decimal"
        }
    }

    val fractionDigits: Int get() = value.substringAfter('.', "").length
    val isZero: Boolean get() = value == "0"

    override fun compareTo(other: DecimalValue): Int {
        val (left, right) = alignedDigits(other)
        return left.compareTo(right)
    }

    fun add(other: DecimalValue): DecimalValue = calculate(other, subtract = false)

    fun multiply(other: DecimalValue): DecimalValue {
        if (isZero || other.isZero) return ZERO
        val left = value.replace(".", "")
        val right = other.value.replace(".", "")
        val result = IntArray(left.length + right.length)
        for (leftIndex in left.indices.reversed()) {
            for (rightIndex in right.indices.reversed()) {
                val index = leftIndex + rightIndex + 1
                val product = left[leftIndex].digitToInt() * right[rightIndex].digitToInt() + result[index]
                result[index] = product % 10
                result[index - 1] += product / 10
            }
        }
        val scale = fractionDigits + other.fractionDigits
        val digits = result.joinToString("").trimStart('0').padStart(scale + 1, '0')
        return parse(if (scale == 0) digits else {
            digits.dropLast(scale) + "." + digits.takeLast(scale)
        })
    }

    fun subtract(other: DecimalValue): DecimalValue {
        require(this >= other) { "Decimal result must not be negative" }
        return calculate(other, subtract = true)
    }

    private fun alignedDigits(other: DecimalValue): Pair<String, String> {
        val scale = maxOf(fractionDigits, other.fractionDigits)
        fun digits(number: DecimalValue) = number.value.replace(".", "") +
            "0".repeat(scale - number.fractionDigits)
        val left = digits(this)
        val right = digits(other)
        val length = maxOf(left.length, right.length)
        return left.padStart(length, '0') to right.padStart(length, '0')
    }

    private fun calculate(other: DecimalValue, subtract: Boolean): DecimalValue {
        val (left, right) = alignedDigits(other)
        val result = StringBuilder()
        var carry = 0
        for (index in left.indices.reversed()) {
            val digit = left[index].digitToInt() + carry +
                (if (subtract) -right[index].digitToInt() else right[index].digitToInt())
            result.append(('0'.code + (digit + 10) % 10).toChar())
            carry = if (subtract) { if (digit < 0) -1 else 0 } else digit / 10
        }
        if (carry > 0) result.append('1')
        val scale = maxOf(fractionDigits, other.fractionDigits)
        val digits = result.reverse().toString().padStart(scale + 1, '0')
        return parse(if (scale == 0) digits else
            digits.dropLast(scale) + "." + digits.takeLast(scale))
    }

    companion object {
        val ZERO = DecimalValue("0")

        /** Accepts plain decimal notation with a dot; never rounds or converts through Double. */
        fun parse(text: String): DecimalValue {
            require(text.matches(Regex("[0-9]+(?:\\.[0-9]+)?"))) { "Invalid decimal format" }
            val whole = text.substringBefore('.').trimStart('0').ifEmpty { "0" }
            val fraction = text.substringAfter('.', "").trimEnd('0')
            return DecimalValue(whole + if (fraction.isEmpty()) "" else ".$fraction")
        }
    }
}
