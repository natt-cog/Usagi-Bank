package jp.usagi.bank.service

import jp.usagi.bank.domain.Account
import jp.usagi.bank.domain.Transaction
import jp.usagi.bank.domain.TransactionType
import jp.usagi.bank.repository.AccountRepository
import jp.usagi.bank.repository.TransactionRepository
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicLong

/**
 * 行内振込.
 *
 * 手数料体系 (税込):
 *   同一店内            :   0円
 *   本支店間 3万円未満  : 110円
 *   本支店間 3万円以上  : 220円
 */
@Service
class TransferService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val accountService: AccountService,
    private val businessDateService: BusinessDateService,
    @Value("\${usagi.transfer.daily-limit:1000000}") val dailyLimit: BigDecimal,
) {
    private val sequence = AtomicLong(1)

    fun calculateFee(
        from: Account,
        to: Account,
        amount: BigDecimal,
    ): BigDecimal {
        if (from.branchCode == to.branchCode) {
            return BigDecimal.ZERO
        }
        return if (amount < FEE_THRESHOLD) FEE_LOW else FEE_HIGH
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(value = ["branchTotals"], allEntries = true)
    fun transfer(
        fromBranch: String?,
        fromNo: String?,
        toBranch: String?,
        toNo: String?,
        amount: BigDecimal?,
        description: String?,
        operatorId: String?,
    ): TransferResult {
        AccountService.validateAmount(amount)
        val amt: BigDecimal = checkNotNull(amount)
        if (fromBranch == toBranch && fromNo == toNo) {
            throw BankingException("UB-2003", "同一口座への振込はできません")
        }

        // デッドロック回避のため店番・口座番号順にロック取得
        val first: Account
        val second: Account
        val fromIsFirst = "$fromBranch$fromNo" < "$toBranch$toNo"
        if (fromIsFirst) {
            first = lock(fromBranch, fromNo)
            second = lock(toBranch, toNo)
        } else {
            first = lock(toBranch, toNo)
            second = lock(fromBranch, fromNo)
        }
        val from = if (fromIsFirst) first else second
        val to = if (fromIsFirst) second else first

        accountService.ensureActive(from)
        accountService.ensureActive(to)

        val fee = calculateFee(from, to, amt)
        val total = amt + fee
        if (from.balance < total) {
            throw InsufficientFundsException(from, total)
        }
        val alreadyToday: BigDecimal =
            transactionRepository.sumTransferOutOn(from.id, ISO_DATE.print(businessDateService.today()))
        if (alreadyToday + amt > dailyLimit) {
            throw TransferLimitExceededException(dailyLimit)
        }

        val ref = nextReference()
        val payeeName: String? = checkNotNull(to.customer).nameKana
        val payerName: String? = checkNotNull(from.customer).nameKana

        from.balance = from.balance.subtract(amt)
        val debit =
            accountService.post(
                from,
                TransactionType.TRANSFER_OUT,
                amt,
                "振込 $payeeName" + (if (description == null) "" else " $description"),
                ref,
                operatorId,
            )
        var feeTxn: Transaction? = null
        if (fee.signum() > 0) {
            from.balance = from.balance.subtract(fee)
            feeTxn = accountService.post(from, TransactionType.TRANSFER_FEE, fee, "振込手数料", ref, operatorId)
        }
        to.balance = to.balance.add(amt)
        val credit =
            accountService.post(
                to,
                TransactionType.TRANSFER_IN,
                amt,
                "振込 $payerName" + (if (description == null) "" else " $description"),
                ref,
                operatorId,
            )

        return TransferResult(ref, debit, feeTxn, credit)
    }

    private fun lock(
        branchCode: String?,
        accountNo: String?,
    ): Account {
        val account: Account? = accountRepository.findForUpdate(branchCode, accountNo)
        return account ?: throw AccountNotFoundException(branchCode, accountNo)
    }

    private fun nextReference(): String =
        "T" + REF_DATE.print(businessDateService.today()) + String.format("%06d", sequence.getAndIncrement())

    class TransferResult(
        val referenceNo: String,
        val debit: Transaction,
        val fee: Transaction?,
        val credit: Transaction,
    ) {
        val feeAmount: BigDecimal?
            get() = if (fee == null) BigDecimal.ZERO else fee.amount
    }

    companion object {
        private val REF_DATE: DateTimeFormatter = DateTimeFormat.forPattern("yyyyMMdd")
        private val ISO_DATE: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")
        private val FEE_THRESHOLD = BigDecimal("30000")
        private val FEE_LOW = BigDecimal("110")
        private val FEE_HIGH = BigDecimal("220")
    }
}
