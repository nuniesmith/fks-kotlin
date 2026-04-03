package xyz.fkstrading.clients.ui.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatUtilsTest {
    @Test
    fun testFormatDecimal_ZeroDecimals() {
        val result = formatDecimal(1234.567, 0)
        assertEquals("1234", result, "Zero decimals should return integer part only")
    }

    @Test
    fun testFormatDecimal_TwoDecimals() {
        val result = formatDecimal(1234.567, 2)
        assertEquals("1234.56", result, "Should format to 2 decimal places")
    }

    @Test
    fun testFormatDecimal_FourDecimals() {
        val result = formatDecimal(1234.5678, 4)
        assertEquals("1234.5678", result, "Should format to 4 decimal places")
    }

    @Test
    fun testFormatDecimal_Rounding() {
        val result = formatDecimal(1234.999, 2)
        assertEquals("1234.99", result, "Should round down correctly")
    }

    @Test
    fun testFormatDecimal_NegativeDecimals() {
        val result = formatDecimal(1234.567, -1)
        assertEquals("1234.567", result, "Negative decimals should return original value")
    }

    @Test
    fun testFormatNumberWithCommas_DefaultDecimals() {
        val result = formatNumberWithCommas(1234567.89)
        assertEquals("1,234,567.89", result, "Should add commas and format to 2 decimals")
    }

    @Test
    fun testFormatNumberWithCommas_CustomDecimals() {
        val result = formatNumberWithCommas(1234567.89123, 4)
        assertEquals("1,234,567.8912", result, "Should respect custom decimal places")
    }

    @Test
    fun testFormatNumberWithCommas_SmallNumber() {
        val result = formatNumberWithCommas(123.45)
        assertEquals("123.45", result, "Small numbers don't need commas")
    }

    @Test
    fun testFormatNumberWithCommas_LargeNumber() {
        val result = formatNumberWithCommas(1234567890.12)
        assertEquals("1,234,567,890.12", result, "Should handle large numbers with multiple commas")
    }

    @Test
    fun testFormatPercent_DefaultDecimals() {
        val result = formatPercent(12.345)
        assertEquals("12.34%", result, "Should format as percentage with 2 decimals")
    }

    @Test
    fun testFormatPercent_CustomDecimals() {
        val result = formatPercent(12.345, 1)
        assertEquals("12.3%", result, "Should respect custom decimal places")
    }

    @Test
    fun testFormatPercent_Zero() {
        val result = formatPercent(0.0)
        assertEquals("0.00%", result, "Should format zero correctly")
    }

    @Test
    fun testFormatPercent_Negative() {
        val result = formatPercent(-5.67)
        assertEquals("-5.67%", result, "Should handle negative percentages")
    }
}
