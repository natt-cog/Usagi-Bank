package jp.usagi.bank.service

import jp.usagi.bank.domain.Account
import jp.usagi.bank.domain.AccountStatus
import jp.usagi.bank.domain.AccountType
import jp.usagi.bank.domain.TransactionType
import jp.usagi.bank.repository.AccountRepository
import org.joda.time.LocalDate
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * 利息計算.
 *
 * 日次積数方式:
 *   日割利息 = 残高 × 年利(%) ÷ 100 ÷ 365   (銭未満切捨, 閏年も365日)
 *   未払利息に日次加算し, 決算日 (2/20, 8/20) に円未満切捨で入金.
 *   源泉税 20.315% (所得税15.315% + 住民税5%) は円未満切捨.
 *   対象科目: 普通・貯蓄・定期. 当座は無利息.
 *
 * ホスト側 COBOL バッチ (cobol/INTCALC.cbl) と計算結果が一致しなければならない.
 */
@Service
class InterestService(
    private val accountRepository: AccountRepository,
    private val accountService: AccountService,
    private val businessDateService: BusinessDateService,
) {
    /** 日次利息積数. 全有効口座の未払利息を1日分加算する. */
    @Transactional
    fun accrueDaily(): Int {
        var count = 0
        val accounts: List<Account> =
            accountRepository.findByStatusOrderByBranchCodeAscAccountNoAsc(AccountStatus.ACTIVE)
        for (account in accounts) {
            if (!bearsInterest(account)) {
                continue
            }
            val interest = dailyInterest(account.balance, account.interestRate)
            account.accruedInterest = account.accruedInterest.add(interest)
            accountRepository.save(account)
            count++
        }
        log.info("日次利息積数 完了 対象口座数={}", count)
        return count
    }

    /** 利息決算. 未払利息を円未満切捨で入金し, 源泉税を出金する. */
    @Transactional
    fun postInterest(): Int {
        val today: LocalDate = businessDateService.today()
        var count = 0
        val accounts: List<Account> =
            accountRepository.findByStatusOrderByBranchCodeAscAccountNoAsc(AccountStatus.ACTIVE)
        for (account in accounts) {
            val gross = account.accruedInterest.setScale(0, BigDecimal.ROUND_DOWN)
            if (gross.signum() <= 0) {
                continue
            }
            val tax = gross.multiply(TAX_RATE).setScale(0, BigDecimal.ROUND_DOWN)
            val period = if (today.monthOfYear == 2) "下期" else "上期"

            account.balance = account.balance.add(gross)
            accountService.post(account, TransactionType.INTEREST, gross, "利息 $period", null, "BATCH")
            if (tax.signum() > 0) {
                account.balance = account.balance.subtract(tax)
                accountService.post(account, TransactionType.TAX, tax, "利息源泉税", null, "BATCH")
            }
            account.accruedInterest = account.accruedInterest.subtract(gross)
            accountRepository.save(account)
            count++
        }
        log.info("利息決算 完了 対象口座数={}", count)
        return count
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(InterestService::class.java)

        /** パッケージ内部用 */
        @JvmField
        val DAYS_IN_YEAR: BigDecimal = BigDecimal("365")

        /** パッケージ内部用 */
        @JvmField
        val HUNDRED: BigDecimal = BigDecimal("100")

        /** パッケージ内部用 */
        @JvmField
        val TAX_RATE: BigDecimal = BigDecimal("0.20315")

        /** 1日分の利息. 銭未満切捨. */
        @JvmStatic
        fun dailyInterest(
            balance: BigDecimal,
            annualRatePercent: BigDecimal,
        ): BigDecimal {
            if (balance.signum() <= 0) {
                return BigDecimal.ZERO.setScale(2)
            }
            return balance
                .multiply(annualRatePercent)
                .divide(HUNDRED, 10, BigDecimal.ROUND_DOWN)
                .divide(DAYS_IN_YEAR, 2, BigDecimal.ROUND_DOWN)
        }

        @JvmStatic
        fun bearsInterest(account: Account): Boolean =
            account.status == AccountStatus.ACTIVE && account.accountType != AccountType.CURRENT
    }
}
