package jp.usagi.bank.domain

import org.hibernate.validator.constraints.NotBlank
import org.springframework.format.annotation.DateTimeFormat
import java.util.ArrayList
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

/**
 * 顧客 (CIF).
 *
 * 登録フォーム (`CustomerController`) から直接バインドされるため, 必須項目も検証前は null を取り得る.
 */
@Entity
@Table(name = "CUSTOMER")
class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_seq")
    @SequenceGenerator(name = "customer_seq", sequenceName = "SEQ_CUSTOMER", allocationSize = 1)
    @Column(name = "CUSTOMER_ID")
    var id: Long? = null

    /** CIF番号 (10桁). 登録時は CustomerService が採番する. */
    @Pattern(regexp = "\\d{10}")
    @Column(name = "CIF_NO", nullable = false, unique = true, length = 10)
    var cifNo: String? = null

    /** 氏名 (漢字). */
    @NotBlank
    @Size(max = 60)
    @Column(name = "NAME_KANJI", nullable = false, length = 60)
    var nameKanji: String? = null

    /** 氏名 (カナ). 全銀フォーマット用に半角カナで保持. */
    @NotBlank
    @Size(max = 60)
    @Column(name = "NAME_KANA", nullable = false, length = 60)
    var nameKana: String? = null

    @NotNull
    @DateTimeFormat(pattern = "yyyy/MM/dd")
    @Temporal(TemporalType.DATE)
    @Column(name = "BIRTH_DATE", nullable = false)
    var birthDate: Date? = null

    @Size(max = 8)
    @Column(name = "POSTAL_CODE", length = 8)
    var postalCode: String? = null

    @Size(max = 200)
    @Column(name = "ADDRESS", length = 200)
    var address: String? = null

    @Size(max = 15)
    @Column(name = "PHONE", length = 15)
    var phone: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "KYC_STATUS", nullable = false, length = 10)
    var kycStatus: KycStatus = KycStatus.PENDING

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    var createdAt: Date = Date()

    @OneToMany(mappedBy = "customer")
    var accounts: MutableList<Account> = ArrayList()
}
