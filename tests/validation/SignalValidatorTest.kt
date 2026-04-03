package xyz.fkstrading.clients.validation

import xyz.fkstrading.clients.data.models.SignalResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignalValidatorTest {
    private fun createValidBuySignal(): SignalResponse {
        return SignalResponse(
            symbol = "BTCUSD",
            signal_type = "BUY",
            category = "swing",
            entry_price = 50000.0,
            take_profit = 55000.0,
            stop_loss = 48000.0,
            take_profit_pct = 10.0,
            stop_loss_pct = 4.0,
            risk_reward_ratio = 2.5,
            position_size_pct = 5.0,
            strength = "STRONG",
            confidence = 0.75,
            timestamp = "2025-01-01T00:00:00Z",
            is_valid = true,
        )
    }

    private fun createValidSellSignal(): SignalResponse {
        return SignalResponse(
            symbol = "ETHUSDT",
            signal_type = "SELL",
            category = "scalp",
            entry_price = 3000.0,
            take_profit = 2850.0,
            stop_loss = 3100.0,
            take_profit_pct = 5.0,
            stop_loss_pct = 3.33,
            risk_reward_ratio = 1.5,
            position_size_pct = 3.0,
            strength = "MEDIUM",
            confidence = 0.65,
            timestamp = "2025-01-01T00:00:00Z",
            is_valid = true,
        )
    }

    @Test
    fun testValidateSignal_ValidBuySignal() {
        val signal = createValidBuySignal()
        val result = SignalValidator.validateSignal(signal)

        assertTrue(result.isValid, "Valid BUY signal should pass validation")
        assertTrue(result.errors.isEmpty(), "Valid signal should have no errors")
    }

    @Test
    fun testValidateSignal_ValidSellSignal() {
        val signal = createValidSellSignal()
        val result = SignalValidator.validateSignal(signal)

        assertTrue(result.isValid, "Valid SELL signal should pass validation")
        assertTrue(result.errors.isEmpty(), "Valid signal should have no errors")
    }

    @Test
    fun testValidateSignal_LowConfidence() {
        val signal = createValidBuySignal().copy(confidence = 0.5)
        val result = SignalValidator.validateSignal(signal)

        assertFalse(result.isValid, "Signal with low confidence should fail validation")
        assertTrue(result.errors.any { it.contains("Confidence") && it.contains("0.6") })
    }

    @Test
    fun testValidateSignal_LowRiskReward() {
        val signal = createValidBuySignal().copy(risk_reward_ratio = 1.2)
        val result = SignalValidator.validateSignal(signal)

        assertFalse(result.isValid, "Signal with low risk/reward should fail validation")
        assertTrue(result.errors.any { it.contains("Risk/Reward") && it.contains("1.5") })
    }

    @Test
    fun testValidateSignal_InvalidEntryPrice() {
        val signal = createValidBuySignal().copy(entry_price = 0.0)
        val result = SignalValidator.validateSignal(signal)

        assertFalse(result.isValid, "Signal with zero entry price should fail")
        assertTrue(result.errors.any { it.contains("Entry price must be greater than 0") })
    }

    @Test
    fun testValidateSignal_InvalidStopLoss_BuySignal() {
        val signal = createValidBuySignal().copy(stop_loss = 51000.0) // Above entry
        val result = SignalValidator.validateSignal(signal)

        assertFalse(result.isValid, "BUY signal with stop loss above entry should fail")
        assertTrue(result.errors.any { it.contains("stop loss must be below entry price") })
    }

    @Test
    fun testValidateSignal_InvalidStopLoss_SellSignal() {
        val signal = createValidSellSignal().copy(stop_loss = 2900.0) // Below entry
        val result = SignalValidator.validateSignal(signal)

        assertFalse(result.isValid, "SELL signal with stop loss below entry should fail")
        assertTrue(result.errors.any { it.contains("stop loss must be above entry price") })
    }

    @Test
    fun testValidateSignal_InvalidTakeProfit_BuySignal() {
        val signal = createValidBuySignal().copy(take_profit = 49000.0) // Below entry
        val result = SignalValidator.validateSignal(signal)

        assertFalse(result.isValid, "BUY signal with take profit below entry should fail")
        assertTrue(result.errors.any { it.contains("take profit must be above entry price") })
    }

    @Test
    fun testValidateSignal_InvalidTakeProfit_SellSignal() {
        val signal = createValidSellSignal().copy(take_profit = 3100.0) // Above entry
        val result = SignalValidator.validateSignal(signal)

        assertFalse(result.isValid, "SELL signal with take profit above entry should fail")
        assertTrue(result.errors.any { it.contains("take profit must be below entry price") })
    }

    @Test
    fun testValidateSignal_EmptySymbol() {
        val signal = createValidBuySignal().copy(symbol = "")
        val result = SignalValidator.validateSignal(signal)

        assertFalse(result.isValid, "Signal with empty symbol should fail")
        assertTrue(result.errors.any { it.contains("Symbol cannot be empty") })
    }

    @Test
    fun testValidateSignal_InvalidSignalType() {
        val signal = createValidBuySignal().copy(signal_type = "HOLD")
        val result = SignalValidator.validateSignal(signal)

        assertFalse(result.isValid, "Signal with invalid type should fail")
        assertTrue(result.errors.any { it.contains("Signal type must be 'BUY' or 'SELL'") })
    }

    @Test
    fun testCalculateRiskReward_BuySignal() {
        val signal = createValidBuySignal()
        val ratio = SignalValidator.calculateRiskReward(signal)

        // Risk = 50000 - 48000 = 2000
        // Reward = 55000 - 50000 = 5000
        // Ratio = 5000 / 2000 = 2.5
        assertEquals(2.5, ratio, 0.01, "Risk/reward ratio should be calculated correctly for BUY")
    }

    @Test
    fun testCalculateRiskReward_SellSignal() {
        val signal = createValidSellSignal()
        val ratio = SignalValidator.calculateRiskReward(signal)

        // Risk = 3100 - 3000 = 100
        // Reward = 3000 - 2850 = 150
        // Ratio = 150 / 100 = 1.5
        assertEquals(1.5, ratio, 0.01, "Risk/reward ratio should be calculated correctly for SELL")
    }

    @Test
    fun testMeetsQualityCriteria_ValidSignal() {
        val signal = createValidBuySignal()
        val meets = SignalValidator.meetsQualityCriteria(signal)

        assertTrue(meets, "Valid signal with good confidence and risk/reward should meet quality criteria")
    }

    @Test
    fun testMeetsQualityCriteria_InvalidSignal() {
        val signal = createValidBuySignal().copy(confidence = 0.5, is_valid = false)
        val meets = SignalValidator.meetsQualityCriteria(signal)

        assertFalse(meets, "Invalid signal should not meet quality criteria")
    }

    @Test
    fun testGetQualityRating_Excellent() {
        val signal = createValidBuySignal().copy(confidence = 0.85, risk_reward_ratio = 2.5)
        val rating = SignalValidator.getQualityRating(signal)

        assertEquals("EXCELLENT", rating, "Signal with high confidence and good R/R should be EXCELLENT")
    }

    @Test
    fun testGetQualityRating_Good() {
        val signal = createValidBuySignal().copy(confidence = 0.75, risk_reward_ratio = 1.8)
        val rating = SignalValidator.getQualityRating(signal)

        assertEquals("GOOD", rating, "Signal with good confidence and R/R should be GOOD")
    }

    @Test
    fun testGetQualityRating_Fair() {
        val signal = createValidBuySignal().copy(confidence = 0.65, risk_reward_ratio = 1.3)
        val rating = SignalValidator.getQualityRating(signal)

        assertEquals("FAIR", rating, "Signal with moderate confidence and R/R should be FAIR")
    }

    @Test
    fun testGetQualityRating_Poor() {
        val signal = createValidBuySignal().copy(confidence = 0.5, risk_reward_ratio = 1.1)
        val rating = SignalValidator.getQualityRating(signal)

        assertEquals("POOR", rating, "Signal with low confidence and R/R should be POOR")
    }

    @Test
    fun testShouldAutoApprove_ReturnsFalse() {
        val signal = createValidBuySignal()
        val shouldApprove = SignalValidator.shouldAutoApprove(signal)

        assertFalse(shouldApprove, "Auto-approval should be disabled by default")
    }
}
