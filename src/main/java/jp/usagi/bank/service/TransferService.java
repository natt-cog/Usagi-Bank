package jp.usagi.bank.service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jp.usagi.bank.domain.Account;
import jp.usagi.bank.domain.Transaction;
import jp.usagi.bank.domain.TransactionType;
import jp.usagi.bank.repository.AccountRepository;
import jp.usagi.bank.repository.TransactionRepository;

/**
 * 行内振込.
 *
 * <pre>
 * 手数料体系 (税込):
 *   同一店内            :   0円
 *   本支店間 3万円未満  : 110円
 *   本支店間 3万円以上  : 220円
 * </pre>
 */
@Service
public class TransferService {

    private static final DateTimeFormatter REF_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final BigDecimal FEE_THRESHOLD = new BigDecimal("30000");
    private static final BigDecimal FEE_LOW = new BigDecimal("110");
    private static final BigDecimal FEE_HIGH = new BigDecimal("220");

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final BusinessDateService businessDateService;
    private final BigDecimal dailyLimit;
    private final AtomicLong sequence = new AtomicLong(1);

    public TransferService(AccountRepository accountRepository, TransactionRepository transactionRepository,
            AccountService accountService, BusinessDateService businessDateService,
            @Value("${usagi.transfer.daily-limit:1000000}") BigDecimal dailyLimit) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.businessDateService = businessDateService;
        this.dailyLimit = dailyLimit;
    }

    public BigDecimal calculateFee(Account from, Account to, BigDecimal amount) {
        if (from.getBranchCode().equals(to.getBranchCode())) {
            return BigDecimal.ZERO;
        }
        return amount.compareTo(FEE_THRESHOLD) < 0 ? FEE_LOW : FEE_HIGH;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(value = "branchTotals", allEntries = true)
    public TransferResult transfer(String fromBranch, String fromNo, String toBranch, String toNo,
            BigDecimal amount, String description, String operatorId) {
        AccountService.validateAmount(amount);
        if (fromBranch.equals(toBranch) && fromNo.equals(toNo)) {
            throw new BankingException("UB-2003", "同一口座への振込はできません");
        }

        // デッドロック回避のため店番・口座番号順にロック取得
        Account first;
        Account second;
        boolean fromIsFirst = (fromBranch + fromNo).compareTo(toBranch + toNo) < 0;
        if (fromIsFirst) {
            first = lock(fromBranch, fromNo);
            second = lock(toBranch, toNo);
        } else {
            first = lock(toBranch, toNo);
            second = lock(fromBranch, fromNo);
        }
        Account from = fromIsFirst ? first : second;
        Account to = fromIsFirst ? second : first;

        accountService.ensureActive(from);
        accountService.ensureActive(to);

        BigDecimal fee = calculateFee(from, to, amount);
        BigDecimal total = amount.add(fee);
        if (from.getBalance().compareTo(total) < 0) {
            throw new InsufficientFundsException(from, total);
        }
        BigDecimal alreadyToday = transactionRepository.sumTransferOutOn(from.getId(),
                businessDateService.today().format(ISO_DATE));
        if (alreadyToday.add(amount).compareTo(dailyLimit) > 0) {
            throw new TransferLimitExceededException(dailyLimit);
        }

        String ref = nextReference();
        String payeeName = to.getCustomer().getNameKana();
        String payerName = from.getCustomer().getNameKana();

        from.setBalance(from.getBalance().subtract(amount));
        Transaction debit = accountService.post(from, TransactionType.TRANSFER_OUT, amount,
                "振込 " + payeeName + (description == null ? "" : " " + description), ref, operatorId);
        Transaction feeTxn = null;
        if (fee.signum() > 0) {
            from.setBalance(from.getBalance().subtract(fee));
            feeTxn = accountService.post(from, TransactionType.TRANSFER_FEE, fee, "振込手数料", ref, operatorId);
        }
        to.setBalance(to.getBalance().add(amount));
        Transaction credit = accountService.post(to, TransactionType.TRANSFER_IN, amount,
                "振込 " + payerName + (description == null ? "" : " " + description), ref, operatorId);

        return new TransferResult(ref, debit, feeTxn, credit);
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    private Account lock(String branchCode, String accountNo) {
        Account account = accountRepository.findForUpdate(branchCode, accountNo);
        if (account == null) {
            throw new AccountNotFoundException(branchCode, accountNo);
        }
        return account;
    }

    private String nextReference() {
        return "T" + businessDateService.today().format(REF_DATE) + String.format("%06d", sequence.getAndIncrement());
    }

    public static class TransferResult {
        private final String referenceNo;
        private final Transaction debit;
        private final Transaction fee;
        private final Transaction credit;

        public TransferResult(String referenceNo, Transaction debit, Transaction fee, Transaction credit) {
            this.referenceNo = referenceNo;
            this.debit = debit;
            this.fee = fee;
            this.credit = credit;
        }

        public String getReferenceNo() {
            return referenceNo;
        }

        public Transaction getDebit() {
            return debit;
        }

        public Transaction getFee() {
            return fee;
        }

        public Transaction getCredit() {
            return credit;
        }

        public BigDecimal getFeeAmount() {
            return fee == null ? BigDecimal.ZERO : fee.getAmount();
        }
    }
}
