package jp.usagi.bank.repository;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jp.usagi.bank.domain.Transaction;
import jp.usagi.bank.domain.TransactionType;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByAccountIdOrderByPostedAtDescIdDesc(Long accountId, Pageable pageable);

    List<Transaction> findByAccountIdAndValueDateBetweenOrderByPostedAtAscIdAsc(Long accountId, Date from, Date to);

    List<Transaction> findByReferenceNo(String referenceNo);

    long countByAccountIdAndTypeAndValueDate(Long accountId, TransactionType type, Date valueDate);

    /** 当日の振込出金合計 (1日あたり限度額チェック用). */
    @Query(value = "SELECT NVL(SUM(AMOUNT), 0) FROM TRANSACTION"
            + " WHERE ACCOUNT_ID = :accountId AND TXN_TYPE = 'TRANSFER_OUT'"
            + " AND VALUE_DATE = TO_DATE(:valueDate, 'YYYY-MM-DD')", nativeQuery = true)
    BigDecimal sumTransferOutOn(@Param("accountId") Long accountId, @Param("valueDate") String valueDate);
}
