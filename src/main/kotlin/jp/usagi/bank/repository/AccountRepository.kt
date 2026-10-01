package jp.usagi.bank.repository

import jp.usagi.bank.domain.Account
import jp.usagi.bank.domain.AccountStatus
import jp.usagi.bank.domain.AccountType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import javax.persistence.LockModeType

interface AccountRepository : JpaRepository<Account, Long>, AccountRepositoryCustom {

    fun findByBranchCodeAndAccountNo(branchCode: String, accountNo: String): Account?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.branchCode = :branchCode AND a.accountNo = :accountNo")
    fun findForUpdate(@Param("branchCode") branchCode: String, @Param("accountNo") accountNo: String): Account?

    fun findByCustomerIdOrderByOpenedOnAsc(customerId: Long?): List<Account>

    fun findByStatusOrderByBranchCodeAscAccountNoAsc(status: AccountStatus): List<Account>

    fun findByAccountTypeAndStatus(type: AccountType, status: AccountStatus): List<Account>

    @Query(
        value = "SELECT NVL(SUM(BALANCE), 0) FROM ACCOUNT WHERE BRANCH_CODE = :branchCode AND STATUS = 'ACTIVE'",
        nativeQuery = true,
    )
    fun sumBalanceByBranch(@Param("branchCode") branchCode: String): BigDecimal

    @Query(value = "SELECT NVL(MAX(ACCOUNT_NO), '0000000') FROM ACCOUNT WHERE BRANCH_CODE = :branchCode", nativeQuery = true)
    fun findMaxAccountNo(@Param("branchCode") branchCode: String): String
}
