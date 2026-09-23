package jp.usagi.bank.domain;

import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/**
 * 預金口座.
 *
 * 残高は円単位 (JPY は補助通貨なし) のため NUMBER(15,0) で保持する.
 */
@Entity
@Table(name = "ACCOUNT", uniqueConstraints = @UniqueConstraint(columnNames = { "BRANCH_CODE", "ACCOUNT_NO" }))
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_seq")
    @SequenceGenerator(name = "account_seq", sequenceName = "SEQ_ACCOUNT", allocationSize = 1)
    @Column(name = "ACCOUNT_ID")
    private Long id;

    /** 店番 (3桁). */
    @NotNull
    @Pattern(regexp = "\\d{3}")
    @Column(name = "BRANCH_CODE", nullable = false, length = 3)
    private String branchCode;

    /** 口座番号 (7桁). */
    @NotNull
    @Pattern(regexp = "\\d{7}")
    @Column(name = "ACCOUNT_NO", nullable = false, length = 7)
    private String accountNo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "ACCOUNT_TYPE", nullable = false, length = 12)
    private AccountType accountType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private AccountStatus status = AccountStatus.ACTIVE;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CUSTOMER_ID", nullable = false)
    private Customer customer;

    @NotNull
    @Column(name = "BALANCE", nullable = false, precision = 15, scale = 0)
    private BigDecimal balance = BigDecimal.ZERO;

    /** 年利 (%). 例: 0.001 = 0.001%. */
    @NotNull
    @Column(name = "INTEREST_RATE", nullable = false, precision = 7, scale = 4)
    private BigDecimal interestRate = new BigDecimal("0.0010");

    /** 当期未払利息 (銭単位まで保持, 決算時に円へ切捨). */
    @NotNull
    @Column(name = "ACCRUED_INTEREST", nullable = false, precision = 17, scale = 2)
    private BigDecimal accruedInterest = BigDecimal.ZERO;

    @Temporal(TemporalType.DATE)
    @Column(name = "OPENED_ON", nullable = false)
    private Date openedOn = new Date();

    @Temporal(TemporalType.DATE)
    @Column(name = "LAST_TXN_ON")
    private Date lastTransactionOn;

    @Version
    @Column(name = "VERSION_NO")
    private long version;

    /** 表示用の店番-口座番号 (例: 001-1234567). */
    public String getDisplayNo() {
        return branchCode + "-" + accountNo;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public void setBranchCode(String branchCode) {
        this.branchCode = branchCode;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public void setAccountNo(String accountNo) {
        this.accountNo = accountNo;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    public BigDecimal getAccruedInterest() {
        return accruedInterest;
    }

    public void setAccruedInterest(BigDecimal accruedInterest) {
        this.accruedInterest = accruedInterest;
    }

    public Date getOpenedOn() {
        return openedOn;
    }

    public void setOpenedOn(Date openedOn) {
        this.openedOn = openedOn;
    }

    public Date getLastTransactionOn() {
        return lastTransactionOn;
    }

    public void setLastTransactionOn(Date lastTransactionOn) {
        this.lastTransactionOn = lastTransactionOn;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
