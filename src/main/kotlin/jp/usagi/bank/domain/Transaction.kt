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

/** 取引明細 (元帳). 訂正は取消明細の追加で行い, 物理更新はしない. */
@Entity
@Table(name = "TRANSACTION")
class Transaction {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transaction_seq")
    @field:SequenceGenerator(name = "transaction_seq", sequenceName = "SEQ_TRANSACTION", allocationSize = 1)
    @field:Column(name = "TRANSACTION_ID")
    var id: Long? = null

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "ACCOUNT_ID", nullable = false)
    var account: Account? = null

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "TXN_TYPE", nullable = false, length = 15)
    var type: TransactionType? = null

    @field:Column(name = "AMOUNT", nullable = false, precision = 15, scale = 0)
    var amount: BigDecimal? = null

    @field:Column(name = "BALANCE_AFTER", nullable = false, precision = 15, scale = 0)
    var balanceAfter: BigDecimal? = null

    /** 摘要 (全銀: 20桁半角カナ相当). */
    @field:Column(name = "DESCRIPTION", length = 100)
    var description: String? = null

    /** 取引参照番号. 振込では出金/入金/手数料が同じ番号を持つ. */
    @field:Column(name = "REFERENCE_NO", length = 20)
    var referenceNo: String? = null

    /** 取引日 (営業日). */
    @field:Temporal(TemporalType.DATE)
    @field:Column(name = "VALUE_DATE", nullable = false)
    var valueDate: Date? = null

    @field:Temporal(TemporalType.TIMESTAMP)
    @field:Column(name = "POSTED_AT", nullable = false)
    var postedAt: Date = Date()

    @field:Column(name = "OPERATOR_ID", length = 30)
    var operatorId: String? = null
}
