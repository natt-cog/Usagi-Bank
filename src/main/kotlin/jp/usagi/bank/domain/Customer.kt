package jp.usagi.bank.domain

import org.hibernate.validator.constraints.NotBlank
import org.springframework.format.annotation.DateTimeFormat
import java.util.Date
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.OneToMany
import javax.persistence.SequenceGenerator
import javax.persistence.Table
import javax.persistence.Temporal
import javax.persistence.TemporalType
import javax.validation.constraints.NotNull
import javax.validation.constraints.Pattern
import javax.validation.constraints.Size

/** 顧客 (CIF). */
@Entity
@Table(name = "CUSTOMER")
class Customer {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_seq")
    @field:SequenceGenerator(name = "customer_seq", sequenceName = "SEQ_CUSTOMER", allocationSize = 1)
    @field:Column(name = "CUSTOMER_ID")
    var id: Long? = null

    /** CIF番号 (10桁). 登録時は CustomerService が採番する. */
    @field:Pattern(regexp = "\\d{10}")
    @field:Column(name = "CIF_NO", nullable = false, unique = true, length = 10)
    var cifNo: String? = null

    /** 氏名 (漢字). */
    @field:NotBlank
    @field:Size(max = 60)
    @field:Column(name = "NAME_KANJI", nullable = false, length = 60)
    var nameKanji: String? = null

    /** 氏名 (カナ). 全銀フォーマット用に半角カナで保持. */
    @field:NotBlank
    @field:Size(max = 60)
    @field:Column(name = "NAME_KANA", nullable = false, length = 60)
    var nameKana: String? = null

    @field:NotNull
    @field:DateTimeFormat(pattern = "yyyy/MM/dd")
    @field:Temporal(TemporalType.DATE)
    @field:Column(name = "BIRTH_DATE", nullable = false)
    var birthDate: Date? = null

    @field:Size(max = 8)
    @field:Column(name = "POSTAL_CODE", length = 8)
    var postalCode: String? = null

    @field:Size(max = 200)
    @field:Column(name = "ADDRESS", length = 200)
    var address: String? = null

    @field:Size(max = 15)
    @field:Column(name = "PHONE", length = 15)
    var phone: String? = null

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "KYC_STATUS", nullable = false, length = 10)
    var kycStatus: KycStatus = KycStatus.PENDING

    @field:Temporal(TemporalType.TIMESTAMP)
    @field:Column(name = "CREATED_AT", nullable = false, updatable = false)
    var createdAt: Date = Date()

    @field:OneToMany(mappedBy = "customer")
    var accounts: MutableList<Account> = ArrayList()
}
