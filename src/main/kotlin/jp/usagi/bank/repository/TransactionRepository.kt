package jp.usagi.bank.repository

import jp.usagi.bank.domain.Transaction
import jp.usagi.bank.domain.TransactionType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.util.Date

interface TransactionRepository : JpaRepository<Transaction, Long> {
    fun findByAccountIdOrderByPostedAtDescIdDesc(
        accountId: Long?,
        pageable: Pageable,
    ): Page<Transaction>

    fun findByAccountIdAndValueDateBetweenOrderByPostedAtAscIdAsc(
        accountId: Long?,
        from: Date?,
        to: Date?,
    ): List<Transaction>

    fun findByReferenceNo(referenceNo: String?): List<Transaction>

    fun countByAccountIdAndTypeAndValueDate(
        accountId: Long?,
        type: TransactionType?,
        valueDate: Date?,
    ): Long

    /** 当日の振込出金合計 (1日あたり限度額チェック用). */
    @Query(
        value =
            "SELECT NVL(SUM(AMOUNT), 0) FROM TRANSACTION" +
                " WHERE ACCOUNT_ID = :accountId AND TXN_TYPE = 'TRANSFER_OUT'" +
                " AND VALUE_DATE = TO_DATE(:valueDate, 'YYYY-MM-DD')",
        nativeQuery = true,
    )
    fun sumTransferOutOn(
        @Param("accountId") accountId: Long?,
        @Param("valueDate") valueDate: String?,
    ): BigDecimal
}
