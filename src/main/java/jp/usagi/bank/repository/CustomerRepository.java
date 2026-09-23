package jp.usagi.bank.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jp.usagi.bank.domain.Customer;
import jp.usagi.bank.domain.KycStatus;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Customer findByCifNo(String cifNo);

    /** 採番: 現在の最大 CIF 番号 (Oracle 方言). */
    @Query(value = "SELECT NVL(MAX(CIF_NO), '0000000000') FROM CUSTOMER", nativeQuery = true)
    String findMaxCifNo();

    Page<Customer> findByNameKanjiContainingOrNameKanaContaining(String kanji, String kana, Pageable pageable);

    List<Customer> findByKycStatus(KycStatus status);

    /** 口座残高合計上位N件 (Oracle ROWNUM 方言). */
    @Query(value = "SELECT * FROM ("
            + "  SELECT c.*, NVL(SUM(a.BALANCE), 0) AS TOTAL_BAL FROM CUSTOMER c"
            + "  LEFT JOIN ACCOUNT a ON a.CUSTOMER_ID = c.CUSTOMER_ID AND a.STATUS = 'ACTIVE'"
            + "  GROUP BY c.CUSTOMER_ID, c.CIF_NO, c.NAME_KANJI, c.NAME_KANA, c.BIRTH_DATE, c.POSTAL_CODE,"
            + "           c.ADDRESS, c.PHONE, c.KYC_STATUS, c.CREATED_AT"
            + "  ORDER BY TOTAL_BAL DESC"
            + ") WHERE ROWNUM <= :limit", nativeQuery = true)
    List<Customer> findTopByTotalBalance(@Param("limit") int limit);
}
