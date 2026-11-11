package jp.usagi.bank.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jp.usagi.bank.domain.Account;
import jp.usagi.bank.domain.AccountStatus;
import jp.usagi.bank.domain.AccountType;
import jp.usagi.bank.domain.TransactionType;
import jp.usagi.bank.repository.AccountRepository;

/**
 * 利息計算.
 *
 * <pre>
 * 日次積数方式:
 *   日割利息 = 残高 × 年利(%) ÷ 100 ÷ 365   (銭未満切捨, 閏年も365日)
 *   未払利息に日次加算し, 決算日 (2/20, 8/20) に円未満切捨で入金.
 *   源泉税 20.315% (所得税15.315% + 住民税5%) は円未満切捨.
 *   対象科目: 普通・貯蓄・定期. 当座は無利息.
 * </pre>
 *
 * ホスト側 COBOL バッチ (cobol/INTCALC.cbl) と計算結果が一致しなければならない.
 */
@Service
public class InterestService {

    private static final Logger log = LoggerFactory.getLogger(InterestService.class);

    static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");
    static final BigDecimal HUNDRED = new BigDecimal("100");
    static final BigDecimal TAX_RATE = new BigDecimal("0.20315");

    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final BusinessDateService businessDateService;

    public InterestService(AccountRepository accountRepository, AccountService accountService,
            BusinessDateService businessDateService) {
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.businessDateService = businessDateService;
    }

    /** 1日分の利息. 銭未満切捨. */
    public static BigDecimal dailyInterest(BigDecimal balance, BigDecimal annualRatePercent) {
        if (balance.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return balance.multiply(annualRatePercent)
                .divide(HUNDRED, 10, RoundingMode.DOWN)
                .divide(DAYS_IN_YEAR, 2, RoundingMode.DOWN);
    }

    public static boolean bearsInterest(Account account) {
        return account.getStatus() == AccountStatus.ACTIVE && account.getAccountType() != AccountType.CURRENT;
    }

    /** 日次利息積数. 全有効口座の未払利息を1日分加算する. */
    @Transactional
    public int accrueDaily() {
        int count = 0;
        List<Account> accounts = accountRepository.findByStatusOrderByBranchCodeAscAccountNoAsc(AccountStatus.ACTIVE);
        for (Account account : accounts) {
            if (!bearsInterest(account)) {
                continue;
            }
            BigDecimal interest = dailyInterest(account.getBalance(), account.getInterestRate());
            account.setAccruedInterest(account.getAccruedInterest().add(interest));
            accountRepository.save(account);
            count++;
        }
        log.info("日次利息積数 完了 対象口座数={}", count);
        return count;
    }

    /** 利息決算. 未払利息を円未満切捨で入金し, 源泉税を出金する. */
    @Transactional
    public int postInterest() {
        LocalDate today = businessDateService.today();
        int count = 0;
        List<Account> accounts = accountRepository.findByStatusOrderByBranchCodeAscAccountNoAsc(AccountStatus.ACTIVE);
        for (Account account : accounts) {
            BigDecimal gross = account.getAccruedInterest().setScale(0, RoundingMode.DOWN);
            if (gross.signum() <= 0) {
                continue;
            }
            BigDecimal tax = gross.multiply(TAX_RATE).setScale(0, RoundingMode.DOWN);
            String period = today.getMonthValue() == 2 ? "下期" : "上期";

            account.setBalance(account.getBalance().add(gross));
            accountService.post(account, TransactionType.INTEREST, gross, "利息 " + period, null, "BATCH");
            if (tax.signum() > 0) {
                account.setBalance(account.getBalance().subtract(tax));
                accountService.post(account, TransactionType.TAX, tax, "利息源泉税", null, "BATCH");
            }
            account.setAccruedInterest(account.getAccruedInterest().subtract(gross));
            accountRepository.save(account);
            count++;
        }
        log.info("利息決算 完了 対象口座数={}", count);
        return count;
    }
}
