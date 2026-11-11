package jp.usagi.bank.domain;

import java.math.BigDecimal;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/** 取引明細 (元帳). 訂正は取消明細の追加で行い, 物理更新はしない. */
@Entity
@Table(name = "TRANSACTION")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transaction_seq")
    @SequenceGenerator(name = "transaction_seq", sequenceName = "SEQ_TRANSACTION", allocationSize = 1)
    @Column(name = "TRANSACTION_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ACCOUNT_ID", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "TXN_TYPE", nullable = false, length = 15)
    private TransactionType type;

    @Column(name = "AMOUNT", nullable = false, precision = 15, scale = 0)
    private BigDecimal amount;

    @Column(name = "BALANCE_AFTER", nullable = false, precision = 15, scale = 0)
    private BigDecimal balanceAfter;

    /** 摘要 (全銀: 20桁半角カナ相当). */
    @Column(name = "DESCRIPTION", length = 100)
    private String description;

    /** 取引参照番号. 振込では出金/入金/手数料が同じ番号を持つ. */
    @Column(name = "REFERENCE_NO", length = 20)
    private String referenceNo;

    /** 取引日 (営業日). */
    @Temporal(TemporalType.DATE)
    @Column(name = "VALUE_DATE", nullable = false)
    private Date valueDate;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "POSTED_AT", nullable = false)
    private Date postedAt = new Date();

    @Column(name = "OPERATOR_ID", length = 30)
    private String operatorId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public void setReferenceNo(String referenceNo) {
        this.referenceNo = referenceNo;
    }

    public Date getValueDate() {
        return valueDate;
    }

    public void setValueDate(Date valueDate) {
        this.valueDate = valueDate;
    }

    public Date getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Date postedAt) {
        this.postedAt = postedAt;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }
}
