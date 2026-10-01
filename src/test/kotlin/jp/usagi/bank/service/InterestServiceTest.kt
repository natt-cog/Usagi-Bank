package jp.usagi.bank.service

import java.math.BigDecimal
import jp.usagi.bank.domain.Account
import jp.usagi.bank.domain.AccountStatus
import jp.usagi.bank.domain.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 利息計算の単体テスト (円未満切捨て, 銭単位 2 桁). */
class InterestServiceTest {
    @Test
    fun dailyInterest_ordinary_0_001pct() {
        // 1,250,000 円 × 0.0010% ÷ 365 = 0.0342... → 0.03 円
        val i = InterestService.dailyInterest(BigDecimal("1250000"), BigDecimal("0.0010"))
        assertEquals(BigDecimal("0.03"), i)
    }

    @Test
    fun dailyInterest_timeDeposit_2pct() {
        // 30,000,000 円 × 0.0250% ÷ 365 = 20.547... → 20.54 円 (切捨て)
        val i = InterestService.dailyInterest(BigDecimal("30000000"), BigDecimal("0.0250"))
        assertEquals(BigDecimal("20.54"), i)
    }

    @Test
    fun dailyInterest_zeroBalance() {
        assertEquals(
            BigDecimal("0.00"),
            InterestService.dailyInterest(BigDecimal.ZERO, BigDecimal("0.0010")),
        )
    }

    @Test
    fun dailyInterest_neverNegativeRounding() {
        val i = InterestService.dailyInterest(BigDecimal("1"), BigDecimal("0.0010"))
        assertEquals(BigDecimal("0.00"), i)
    }

    @Test
    fun currentAccountsDoNotBearInterest() {
        val current = account(AccountType.CURRENT, AccountStatus.ACTIVE, "0.0000")
        val ordinary = account(AccountType.ORDINARY, AccountStatus.ACTIVE, "0.0010")
        val frozen = account(AccountType.ORDINARY, AccountStatus.FROZEN, "0.0010")
        assertFalse(InterestService.bearsInterest(current))
        assertTrue(InterestService.bearsInterest(ordinary))
        assertFalse(InterestService.bearsInterest(frozen))
    }

    companion object {
        private fun account(type: AccountType, status: AccountStatus, rate: String): Account {
            val a = Account()
            a.accountType = type
            a.status = status
            a.interestRate = BigDecimal(rate)
            a.balance = BigDecimal("1000")
            return a
        }
    }
}
