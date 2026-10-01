package jp.usagi.bank.service

import jp.usagi.bank.domain.Account
import jp.usagi.bank.domain.AccountStatus
import jp.usagi.bank.domain.AccountType
import jp.usagi.bank.domain.Customer
import jp.usagi.bank.domain.Transaction
import jp.usagi.bank.domain.TransactionType
import jp.usagi.bank.repository.AccountRepository
import jp.usagi.bank.repository.CustomerRepository
import jp.usagi.bank.repository.TransactionRepository
import org.apache.commons.lang3.StringUtils
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.Date

/** 口座業務 (開設・入出金・照会). */
@Service
@Transactional
class AccountService(
    private val accountRepository: AccountRepository,
    private val customerRepository: CustomerRepository,
    private val transactionRepository: TransactionRepository,
    private val businessDateService: BusinessDateService,
) {
    @Transactional(readOnly = true)
    fun getAccount(
        branchCode: String?,
        accountNo: String?,
    ): Account {
        val account: Account? = accountRepository.findByBranchCodeAndAccountNo(branchCode, accountNo)
        return account ?: throw AccountNotFoundException(branchCode, accountNo)
    }

    @Transactional(readOnly = true)
    fun getAccount(id: Long?): Account {
        val account: Account? = accountRepository.findOne(id)
        return account ?: throw AccountNotFoundException("?", id.toString())
    }

    @Transactional(readOnly = true)
    fun listActiveAccounts(): List<Account> =
        accountRepository.findByStatusOrderByBranchCodeAscAccountNoAsc(AccountStatus.ACTIVE)

    @Transactional(readOnly = true)
    fun listAccountsOf(customerId: Long?): List<Account> =
        accountRepository.findByCustomerIdOrderByOpenedOnAsc(customerId)

    @Transactional(readOnly = true)
    fun search(
        branchCode: String?,
        type: AccountType?,
        status: AccountStatus?,
        minBalance: BigDecimal?,
        maxBalance: BigDecimal?,
    ): List<Account> = accountRepository.search(branchCode, type, status, minBalance, maxBalance, 200)

    @Transactional(readOnly = true)
    fun listTransactions(
        account: Account,
        page: Int,
        size: Int,
    ): Page<Transaction> =
        transactionRepository.findByAccountIdOrderByPostedAtDescIdDesc(account.id, PageRequest(page, size))

    @Transactional(readOnly = true)
    @Cacheable("branchTotals")
    fun totalBalanceOfBranch(branchCode: String?): BigDecimal =
        accountRepository.sumBalanceByBranch(branchCode)

    @CacheEvict(value = ["branchTotals"], allEntries = true)
    fun openAccount(
        cifNo: String?,
        branchCode: String?,
        type: AccountType?,
        initialDeposit: BigDecimal?,
        operatorId: String?,
    ): Account {
        val customer: Customer? = customerRepository.findByCifNo(cifNo)
        if (customer == null) {
            throw CustomerNotFoundException(cifNo)
        }
        var account = Account()
        account.customer = customer
        account.branchCode = branchCode
        account.accountNo = nextAccountNo(branchCode)
        account.accountType = type
        account.interestRate =
            if (type == AccountType.TIME_DEPOSIT) {
                DEFAULT_TIME_DEPOSIT_RATE
            } else {
                DEFAULT_ORDINARY_RATE
            }
        account.openedOn = businessDateService.todayAsDate()
        account = accountRepository.save(account)

        if (initialDeposit != null && initialDeposit.signum() > 0) {
            deposit(account, initialDeposit, "口座開設", operatorId)
        }
        return account
    }

    @CacheEvict(value = ["branchTotals"], allEntries = true)
    fun deposit(
        account: Account,
        amount: BigDecimal?,
        description: String?,
        operatorId: String?,
    ): Transaction {
        val amt = requireValidAmount(amount)
        ensureActive(account)
        account.balance = account.balance.add(amt)
        return post(account, TransactionType.DEPOSIT, amt, description, null, operatorId)
    }

    @CacheEvict(value = ["branchTotals"], allEntries = true)
    fun withdraw(
        account: Account,
        amount: BigDecimal?,
        description: String?,
        operatorId: String?,
    ): Transaction {
        val amt = requireValidAmount(amount)
        ensureActive(account)
        if (account.balance < amt) {
            throw InsufficientFundsException(account, amt)
        }
        account.balance = account.balance.subtract(amt)
        return post(account, TransactionType.WITHDRAWAL, amt, description, null, operatorId)
    }

    fun changeStatus(
        account: Account,
        status: AccountStatus,
    ) {
        if (status == AccountStatus.CLOSED && account.balance.signum() != 0) {
            throw BankingException("UB-1004", "残高がある口座は解約できません: ${account.displayNo}")
        }
        account.status = status
        accountRepository.save(account)
    }

    /** パッケージ内部用. 明細を元帳へ記帳する. 残高は呼び出し側で更新済みであること. */
    fun post(
        account: Account,
        type: TransactionType,
        amount: BigDecimal,
        description: String?,
        referenceNo: String?,
        operatorId: String?,
    ): Transaction {
        val today = businessDateService.todayAsDate()
        val txn = Transaction()
        txn.account = account
        txn.type = type
        txn.amount = amount
        txn.balanceAfter = account.balance
        txn.description = StringUtils.abbreviate(description, 100)
        txn.referenceNo = referenceNo
        txn.valueDate = today
        txn.postedAt = Date()
        txn.operatorId = operatorId
        account.lastTransactionOn = today
        accountRepository.save(account)
        return transactionRepository.save(txn)
    }

    /** パッケージ内部用 */
    fun ensureActive(account: Account) {
        if (!account.isActive) {
            throw AccountNotActiveException(account)
        }
    }

    private fun nextAccountNo(branchCode: String?): String {
        val next = accountRepository.findMaxAccountNo(branchCode).toInt() + 1
        return String.format("%07d", next)
    }

    companion object {
        private val DEFAULT_ORDINARY_RATE = BigDecimal("0.0010")
        private val DEFAULT_TIME_DEPOSIT_RATE = BigDecimal("0.0200")

        /** パッケージ内部用 */
        @JvmStatic
        fun validateAmount(amount: BigDecimal?) {
            requireValidAmount(amount)
        }

        private fun requireValidAmount(amount: BigDecimal?): BigDecimal {
            if (amount == null || amount.signum() <= 0) {
                throw InvalidAmountException("金額は1円以上を指定してください")
            }
            if (amount.stripTrailingZeros().scale() > 0) {
                throw InvalidAmountException("円未満の金額は指定できません")
            }
            return amount
        }
    }
}
