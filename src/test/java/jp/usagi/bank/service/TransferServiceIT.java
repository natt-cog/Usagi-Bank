package jp.usagi.bank.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.math.BigDecimal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import jp.usagi.bank.domain.Account;
import jp.usagi.bank.domain.AccountStatus;
import jp.usagi.bank.domain.TransactionType;
import jp.usagi.bank.repository.TransactionRepository;
import jp.usagi.bank.service.TransferService.TransferResult;

/** 振込業務ロジックの結合テスト (H2 Oracle モード). */
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TransferServiceIT {

    @Autowired
    private TransferService transferService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private BusinessDateService businessDateService;

    @Before
    public void fixBusinessDate() {
        businessDateService.override(new org.joda.time.LocalDate(2018, 3, 15));
    }

    @After
    public void clear() {
        businessDateService.clearOverride();
    }

    @Test
    public void sameBranchTransferHasNoFee() {
        Account from = accountService.getAccount("001", "1000001");
        Account to = accountService.getAccount("001", "1000003");
        assertEquals(BigDecimal.ZERO, transferService.calculateFee(from, to, new BigDecimal("50000")));
    }

    @Test
    public void interBranchFeeDependsOnAmount() {
        Account from = accountService.getAccount("001", "1000001");
        Account to = accountService.getAccount("002", "2000001");
        assertEquals(new BigDecimal("110"), transferService.calculateFee(from, to, new BigDecimal("29999")));
        assertEquals(new BigDecimal("220"), transferService.calculateFee(from, to, new BigDecimal("30000")));
    }

    @Test
    public void transferPostsDebitFeeAndCredit() {
        BigDecimal fromBefore = accountService.getAccount("001", "1000001").getBalance();
        BigDecimal toBefore = accountService.getAccount("002", "2000001").getBalance();

        TransferResult r = transferService.transfer("001", "1000001", "002", "2000001",
                new BigDecimal("100000"), "家賃", "teller");

        assertNotNull(r.getReferenceNo());
        assertEquals(TransactionType.TRANSFER_OUT, r.getDebit().getType());
        assertEquals(TransactionType.TRANSFER_FEE, r.getFee().getType());
        assertEquals(TransactionType.TRANSFER_IN, r.getCredit().getType());
        assertEquals(new BigDecimal("220"), r.getFeeAmount());

        Account from = accountService.getAccount("001", "1000001");
        Account to = accountService.getAccount("002", "2000001");
        assertEquals(fromBefore.subtract(new BigDecimal("100220")), from.getBalance());
        assertEquals(toBefore.add(new BigDecimal("100000")), to.getBalance());
        assertEquals(3, transactionRepository.findByReferenceNo(r.getReferenceNo()).size());
    }

    @Test
    public void sameBranchTransferHasNoFeeTransaction() {
        TransferResult r = transferService.transfer("001", "1000001", "001", "1000003",
                new BigDecimal("1000"), null, "teller");
        assertNull(r.getFee());
        assertEquals(BigDecimal.ZERO, r.getFeeAmount());
    }

    @Test(expected = InsufficientFundsException.class)
    public void insufficientFundsIncludesFee() {
        // 1000003 の残高は 45,200 円. 45,000 円 + 手数料 220 円 > 残高
        transferService.transfer("001", "1000003", "002", "2000001", new BigDecimal("45000"), null, "teller");
    }

    @Test(expected = TransferLimitExceededException.class)
    public void dailyLimitIsCumulative() {
        transferService.transfer("005", "5000001", "001", "1000001", new BigDecimal("600000"), null, "teller");
        transferService.transfer("005", "5000001", "001", "1000001", new BigDecimal("400001"), null, "teller");
    }

    @Test(expected = AccountNotActiveException.class)
    public void frozenAccountCannotTransfer() {
        transferService.transfer("003", "3000003", "001", "1000001", new BigDecimal("1000"), null, "teller");
    }

    @Test(expected = InvalidAmountException.class)
    public void amountMustBePositive() {
        transferService.transfer("001", "1000001", "001", "1000003", new BigDecimal("0"), null, "teller");
    }

    @Test
    public void transferToSelfIsRejected() {
        try {
            transferService.transfer("001", "1000001", "001", "1000001", new BigDecimal("1000"), null, "teller");
            fail();
        } catch (BankingException e) {
            assertEquals("UB-2003", e.getErrorCode());
        }
    }

    @Test
    public void closedAccountKeepsStatus() {
        Account closed = accountService.getAccount("002", "2000002");
        assertEquals(AccountStatus.CLOSED, closed.getStatus());
    }
}
