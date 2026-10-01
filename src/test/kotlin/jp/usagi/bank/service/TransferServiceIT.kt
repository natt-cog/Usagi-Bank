package jp.usagi.bank.service

import jp.usagi.bank.domain.Account
import jp.usagi.bank.domain.AccountStatus
import jp.usagi.bank.domain.TransactionType
import jp.usagi.bank.repository.TransactionRepository
import jp.usagi.bank.service.TransferService.TransferResult
import org.joda.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/** 振込業務ロジックの結合テスト (H2 Oracle モード). */
@RunWith(SpringRunner::class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransferServiceIT {

    @Autowired
    lateinit var transferService: TransferService

    @Autowired
    lateinit var accountService: AccountService

    @Autowired
    lateinit var transactionRepository: TransactionRepository

    @Autowired
    lateinit var businessDateService: BusinessDateService

    @Before
    fun fixBusinessDate() {
        businessDateService.override(LocalDate(2018, 3, 15))
    }

    @After
    fun clear() {
        businessDateService.clearOverride()
    }

    @Test
    fun sameBranchTransferHasNoFee() {
        val from = accountService.getAccount("001", "1000001")
        val to = accountService.getAccount("001", "1000003")
        assertEquals(BigDecimal.ZERO, transferService.calculateFee(from, to, BigDecimal("50000")))
    }

    @Test
    fun interBranchFeeDependsOnAmount() {
        val from = accountService.getAccount("001", "1000001")
        val to = accountService.getAccount("002", "2000001")
        assertEquals(BigDecimal("110"), transferService.calculateFee(from, to, BigDecimal("29999")))
        assertEquals(BigDecimal("220"), transferService.calculateFee(from, to, BigDecimal("30000")))
    }

    @Test
    fun transferPostsDebitFeeAndCredit() {
        val fromBefore = accountService.getAccount("001", "1000001").balance
        val toBefore = accountService.getAccount("002", "2000001").balance

        val r: TransferResult = transferService.transfer(
            "001",
            "1000001",
            "002",
            "2000001",
            BigDecimal("100000"),
            "家賃",
            "teller",
        )

        assertNotNull(r.referenceNo)
        assertEquals(TransactionType.TRANSFER_OUT, r.debit.type)
        val fee = requireNotNull(r.fee) { "inter-branch transfer must post a fee transaction" }
        assertEquals(TransactionType.TRANSFER_FEE, fee.type)
        assertEquals(TransactionType.TRANSFER_IN, r.credit.type)
        assertEquals(BigDecimal("220"), r.feeAmount)

        val from: Account = accountService.getAccount("001", "1000001")
        val to: Account = accountService.getAccount("002", "2000001")
        assertEquals(fromBefore.subtract(BigDecimal("100220")), from.balance)
        assertEquals(toBefore.add(BigDecimal("100000")), to.balance)
        assertEquals(3, transactionRepository.findByReferenceNo(r.referenceNo).size)
    }

    @Test
    fun sameBranchTransferHasNoFeeTransaction() {
        val r: TransferResult = transferService.transfer(
            "001",
            "1000001",
            "001",
            "1000003",
            BigDecimal("1000"),
            null,
            "teller",
        )
        assertNull(r.fee)
        assertEquals(BigDecimal.ZERO, r.feeAmount)
    }

    @Test(expected = InsufficientFundsException::class)
    fun insufficientFundsIncludesFee() {
        // 1000003 の残高は 45,200 円. 45,000 円 + 手数料 220 円 > 残高
        transferService.transfer("001", "1000003", "002", "2000001", BigDecimal("45000"), null, "teller")
    }

    @Test(expected = TransferLimitExceededException::class)
    fun dailyLimitIsCumulative() {
        transferService.transfer("005", "5000001", "001", "1000001", BigDecimal("600000"), null, "teller")
        transferService.transfer("005", "5000001", "001", "1000001", BigDecimal("400001"), null, "teller")
    }

    @Test(expected = AccountNotActiveException::class)
    fun frozenAccountCannotTransfer() {
        transferService.transfer("003", "3000003", "001", "1000001", BigDecimal("1000"), null, "teller")
    }

    @Test(expected = InvalidAmountException::class)
    fun amountMustBePositive() {
        transferService.transfer("001", "1000001", "001", "1000003", BigDecimal("0"), null, "teller")
    }

    @Test
    fun transferToSelfIsRejected() {
        try {
            transferService.transfer("001", "1000001", "001", "1000001", BigDecimal("1000"), null, "teller")
            fail()
        } catch (e: BankingException) {
            assertEquals("UB-2003", e.errorCode)
        }
    }

    @Test
    fun closedAccountKeepsStatus() {
        val closed: Account = accountService.getAccount("002", "2000002")
        assertEquals(AccountStatus.CLOSED, closed.status)
    }
}
