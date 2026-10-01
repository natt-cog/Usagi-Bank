package jp.usagi.bank.domain

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/** 店舗マスタ. */
@Entity
@Table(name = "BRANCH")
class Branch {

    @Id
    @Column(name = "BRANCH_CODE", length = 3)
    lateinit var code: String

    @Column(name = "BRANCH_NAME", nullable = false, length = 40)
    lateinit var name: String

    @Column(name = "BRANCH_NAME_KANA", nullable = false, length = 40)
    lateinit var nameKana: String
}
