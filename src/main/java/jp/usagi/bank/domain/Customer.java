package jp.usagi.bank.domain;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import org.hibernate.validator.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;

/** 顧客 (CIF). */
@Entity
@Table(name = "CUSTOMER")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_seq")
    @SequenceGenerator(name = "customer_seq", sequenceName = "SEQ_CUSTOMER", allocationSize = 1)
    @Column(name = "CUSTOMER_ID")
    private Long id;

    /** CIF番号 (10桁). 登録時は CustomerService が採番する. */
    @Pattern(regexp = "\\d{10}")
    @Column(name = "CIF_NO", nullable = false, unique = true, length = 10)
    private String cifNo;

    /** 氏名 (漢字). */
    @NotBlank
    @Size(max = 60)
    @Column(name = "NAME_KANJI", nullable = false, length = 60)
    private String nameKanji;

    /** 氏名 (カナ). 全銀フォーマット用に半角カナで保持. */
    @NotBlank
    @Size(max = 60)
    @Column(name = "NAME_KANA", nullable = false, length = 60)
    private String nameKana;

    @NotNull
    @DateTimeFormat(pattern = "yyyy/MM/dd")
    @Temporal(TemporalType.DATE)
    @Column(name = "BIRTH_DATE", nullable = false)
    private Date birthDate;

    @Size(max = 8)
    @Column(name = "POSTAL_CODE", length = 8)
    private String postalCode;

    @Size(max = 200)
    @Column(name = "ADDRESS", length = 200)
    private String address;

    @Size(max = 15)
    @Column(name = "PHONE", length = 15)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "KYC_STATUS", nullable = false, length = 10)
    private KycStatus kycStatus = KycStatus.PENDING;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Date createdAt = new Date();

    @OneToMany(mappedBy = "customer")
    private List<Account> accounts = new ArrayList<Account>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCifNo() {
        return cifNo;
    }

    public void setCifNo(String cifNo) {
        this.cifNo = cifNo;
    }

    public String getNameKanji() {
        return nameKanji;
    }

    public void setNameKanji(String nameKanji) {
        this.nameKanji = nameKanji;
    }

    public String getNameKana() {
        return nameKana;
    }

    public void setNameKana(String nameKana) {
        this.nameKana = nameKana;
    }

    public Date getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(Date birthDate) {
        this.birthDate = birthDate;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public void setKycStatus(KycStatus kycStatus) {
        this.kycStatus = kycStatus;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts;
    }
}
