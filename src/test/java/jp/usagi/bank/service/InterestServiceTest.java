package jp.usagi.bank.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.Test;

import jp.usagi.bank.domain.Account;
import jp.usagi.bank.domain.AccountStatus;
import jp.usagi.bank.domain.AccountType;

/** 利息計算の単体テスト (円未満切捨て, 銭単位 2 桁). */
public class InterestServiceTest {

    @Test
    public void dailyInterest_ordinary_0_001pct() {
        // 1,250,000 円 × 0.0010% ÷ 365 = 0.0342... → 0.03 円
        BigDecimal i = InterestService.dailyInterest(new BigDecimal("1250000"), new BigDecimal("0.0010"));
        assertEquals(new BigDecimal("0.03"), i);
    }

    @Test
    public void dailyInterest_timeDeposit_2pct() {
        // 30,000,000 円 × 0.0250% ÷ 365 = 20.547... → 20.54 円 (切捨て)
        BigDecimal i = InterestService.dailyInterest(new BigDecimal("30000000"), new BigDecimal("0.0250"));
        assertEquals(new BigDecimal("20.54"), i);
    }

    @Test
    public void dailyInterest_zeroBalance() {
        assertEquals(new BigDecimal("0.00"),
                InterestService.dailyInterest(BigDecimal.ZERO, new BigDecimal("0.0010")));
    }

    @Test
    public void dailyInterest_neverNegativeRounding() {
        BigDecimal i = InterestService.dailyInterest(new BigDecimal("1"), new BigDecimal("0.0010"));
        assertEquals(new BigDecimal("0.00"), i);
    }

    @Test
    public void currentAccountsDoNotBearInterest() {
        Account current = account(AccountType.CURRENT, AccountStatus.ACTIVE, "0.0000");
        Account ordinary = account(AccountType.ORDINARY, AccountStatus.ACTIVE, "0.0010");
        Account frozen = account(AccountType.ORDINARY, AccountStatus.FROZEN, "0.0010");
        assertFalse(InterestService.bearsInterest(current));
        assertTrue(InterestService.bearsInterest(ordinary));
        assertFalse(InterestService.bearsInterest(frozen));
    }

    private static Account account(AccountType type, AccountStatus status, String rate) {
        Account a = new Account();
        a.setAccountType(type);
        a.setStatus(status);
        a.setInterestRate(new BigDecimal(rate));
        a.setBalance(new BigDecimal("1000"));
        return a;
    }
}
