package jp.usagi.bank.domain

import java.math.BigDecimal
import java.util.Date

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.SequenceGenerator
import javax.persistence.Table
import javax.persistence.Temporal
import javax.persistence.TemporalType
import javax.persistence.UniqueConstraint
import javax.persistence.Version
import javax.validation.constraints.NotNull
import javax.validation.constraints.Pattern

/**
 * 預金口座.
 *
 * 残高は円単位 (JPY は補助通貨なし) のため NUMBER(15,0) で保持する.
 */
@Entity
@Table(name = "ACCOUNT", uniqueConstraints = [UniqueConstraint(columnNames = ["BRANCH_CODE", "ACCOUNT_NO"])])
class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_seq")
    @SequenceGenerator(name = "account_seq", sequenceName = "SEQ_ACCOUNT", allocationSize = 1)
    @Column(name = "ACCOUNT_ID")
    var id: Long? = null

    /** 店番 (3桁). */
    @NotNull
    @Pattern(regexp = "\\d{3}")
    @Column(name = "BRANCH_CODE", nullable = false, length = 3)
    lateinit var branchCode: String

    /** 口座番号 (7桁). */
    @NotNull
    @Pattern(regexp = "\\d{7}")
    @Column(name = "ACCOUNT_NO", nullable = false, length = 7)
    lateinit var accountNo: String

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "ACCOUNT_TYPE", nullable = false, length = 12)
    lateinit var accountType: AccountType

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    var status: AccountStatus = AccountStatus.ACTIVE

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CUSTOMER_ID", nullable = false)
    lateinit var customer: Customer

    @NotNull
    @Column(name = "BALANCE", nullable = false, precision = 15, scale = 0)
    var balance: BigDecimal = BigDecimal.ZERO

    /** 年利 (%). 例: 0.001 = 0.001%. */
    @NotNull
    @Column(name = "INTEREST_RATE", nullable = false, precision = 7, scale = 4)
    var interestRate: BigDecimal = BigDecimal("0.0010")

    /** 当期未払利息 (銭単位まで保持, 決算時に円へ切捨). */
    @NotNull
    @Column(name = "ACCRUED_INTEREST", nullable = false, precision = 17, scale = 2)
    var accruedInterest: BigDecimal = BigDecimal.ZERO

    @Temporal(TemporalType.DATE)
    @Column(name = "OPENED_ON", nullable = false)
    var openedOn: Date = Date()

    @Temporal(TemporalType.DATE)
    @Column(name = "LAST_TXN_ON")
    var lastTransactionOn: Date? = null

    @Version
    @Column(name = "VERSION_NO")
    var version: Long = 0

    /** 表示用の店番-口座番号 (例: 001-1234567). */
    val displayNo: String
        get() = "$branchCode-$accountNo"

    val isActive: Boolean
        get() = status == AccountStatus.ACTIVE
}
