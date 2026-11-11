package jp.usagi.bank.repository;

import java.math.BigDecimal;
import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jp.usagi.bank.domain.Account;
import jp.usagi.bank.domain.AccountStatus;
import jp.usagi.bank.domain.AccountType;

public interface AccountRepository extends JpaRepository<Account, Long>, AccountRepositoryCustom {

    Account findByBranchCodeAndAccountNo(String branchCode, String accountNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.branchCode = :branchCode AND a.accountNo = :accountNo")
    Account findForUpdate(@Param("branchCode") String branchCode, @Param("accountNo") String accountNo);

    List<Account> findByCustomerIdOrderByOpenedOnAsc(Long customerId);

    List<Account> findByStatusOrderByBranchCodeAscAccountNoAsc(AccountStatus status);

    List<Account> findByAccountTypeAndStatus(AccountType type, AccountStatus status);

    @Query(value = "SELECT NVL(SUM(BALANCE), 0) FROM ACCOUNT WHERE BRANCH_CODE = :branchCode AND STATUS = 'ACTIVE'",
            nativeQuery = true)
    BigDecimal sumBalanceByBranch(@Param("branchCode") String branchCode);

    @Query(value = "SELECT NVL(MAX(ACCOUNT_NO), '0000000') FROM ACCOUNT WHERE BRANCH_CODE = :branchCode", nativeQuery = true)
    String findMaxAccountNo(@Param("branchCode") String branchCode);
}
