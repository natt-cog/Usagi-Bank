package jp.usagi.bank.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jp.usagi.bank.domain.Account;
import jp.usagi.bank.domain.AccountStatus;
import jp.usagi.bank.domain.AccountType;
import jp.usagi.bank.domain.Customer;
import jp.usagi.bank.domain.Transaction;
import jp.usagi.bank.domain.TransactionType;
import jp.usagi.bank.repository.AccountRepository;
import jp.usagi.bank.repository.CustomerRepository;
import jp.usagi.bank.repository.TransactionRepository;

/** 口座業務 (開設・入出金・照会). */
@Service
@Transactional
public class AccountService {

    private static final BigDecimal DEFAULT_ORDINARY_RATE = new BigDecimal("0.0010");
    private static final BigDecimal DEFAULT_TIME_DEPOSIT_RATE = new BigDecimal("0.0200");

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final BusinessDateService businessDateService;

    public AccountService(AccountRepository accountRepository, CustomerRepository customerRepository,
            TransactionRepository transactionRepository, BusinessDateService businessDateService) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.businessDateService = businessDateService;
    }

    @Transactional(readOnly = true)
    public Account getAccount(String branchCode, String accountNo) {
        Account account = accountRepository.findByBranchCodeAndAccountNo(branchCode, accountNo);
        if (account == null) {
            throw new AccountNotFoundException(branchCode, accountNo);
        }
        return account;
    }

    @Transactional(readOnly = true)
    public Account getAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("?", String.valueOf(id)));
    }

    @Transactional(readOnly = true)
    public List<Account> listActiveAccounts() {
        return accountRepository.findByStatusOrderByBranchCodeAscAccountNoAsc(AccountStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Account> listAccountsOf(Long customerId) {
        return accountRepository.findByCustomerIdOrderByOpenedOnAsc(customerId);
    }

    @Transactional(readOnly = true)
    public List<Account> search(String branchCode, AccountType type, AccountStatus status,
            BigDecimal minBalance, BigDecimal maxBalance) {
        return accountRepository.search(branchCode, type, status, minBalance, maxBalance, 200);
    }

    @Transactional(readOnly = true)
    public Page<Transaction> listTransactions(Account account, int page, int size) {
        return transactionRepository.findByAccountIdOrderByPostedAtDescIdDesc(account.getId(), PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    @Cacheable("branchTotals")
    public BigDecimal totalBalanceOfBranch(String branchCode) {
        return accountRepository.sumBalanceByBranch(branchCode);
    }

    @CacheEvict(value = "branchTotals", allEntries = true)
    public Account openAccount(String cifNo, String branchCode, AccountType type, BigDecimal initialDeposit, String operatorId) {
        Customer customer = customerRepository.findByCifNo(cifNo);
        if (customer == null) {
            throw new CustomerNotFoundException(cifNo);
        }
        Account account = new Account();
        account.setCustomer(customer);
        account.setBranchCode(branchCode);
        account.setAccountNo(nextAccountNo(branchCode));
        account.setAccountType(type);
        account.setInterestRate(type == AccountType.TIME_DEPOSIT ? DEFAULT_TIME_DEPOSIT_RATE : DEFAULT_ORDINARY_RATE);
        account.setOpenedOn(businessDateService.todayAsDate());
        account = accountRepository.save(account);

        if (initialDeposit != null && initialDeposit.signum() > 0) {
            deposit(account, initialDeposit, "口座開設", operatorId);
        }
        return account;
    }

    @CacheEvict(value = "branchTotals", allEntries = true)
    public Transaction deposit(Account account, BigDecimal amount, String description, String operatorId) {
        validateAmount(amount);
        ensureActive(account);
        account.setBalance(account.getBalance().add(amount));
        return post(account, TransactionType.DEPOSIT, amount, description, null, operatorId);
    }

    @CacheEvict(value = "branchTotals", allEntries = true)
    public Transaction withdraw(Account account, BigDecimal amount, String description, String operatorId) {
        validateAmount(amount);
        ensureActive(account);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(account, amount);
        }
        account.setBalance(account.getBalance().subtract(amount));
        return post(account, TransactionType.WITHDRAWAL, amount, description, null, operatorId);
    }

    public void changeStatus(Account account, AccountStatus status) {
        if (status == AccountStatus.CLOSED && account.getBalance().signum() != 0) {
            throw new BankingException("UB-1004", "残高がある口座は解約できません: " + account.getDisplayNo());
        }
        account.setStatus(status);
        accountRepository.save(account);
    }

    /** 明細を元帳へ記帳する. 残高は呼び出し側で更新済みであること. */
    Transaction post(Account account, TransactionType type, BigDecimal amount, String description,
            String referenceNo, String operatorId) {
        Date today = businessDateService.todayAsDate();
        Transaction txn = new Transaction();
        txn.setAccount(account);
        txn.setType(type);
        txn.setAmount(amount);
        txn.setBalanceAfter(account.getBalance());
        txn.setDescription(StringUtils.abbreviate(description, 100));
        txn.setReferenceNo(referenceNo);
        txn.setValueDate(today);
        txn.setPostedAt(new Date());
        txn.setOperatorId(operatorId);
        account.setLastTransactionOn(today);
        accountRepository.save(account);
        return transactionRepository.save(txn);
    }

    void ensureActive(Account account) {
        if (!account.isActive()) {
            throw new AccountNotActiveException(account);
        }
    }

    static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException("金額は1円以上を指定してください");
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new InvalidAmountException("円未満の金額は指定できません");
        }
    }

    private String nextAccountNo(String branchCode) {
        String max = accountRepository.findMaxAccountNo(branchCode);
        int next = Integer.parseInt(max) + 1;
        return String.format("%07d", next);
    }
}
