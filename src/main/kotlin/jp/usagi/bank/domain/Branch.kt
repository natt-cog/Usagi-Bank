package jp.usagi.bank.domain

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/** 店舗マスタ. */
@Entity
@Table(name = "BRANCH")
class Branch {

    @field:Id
    @field:Column(name = "BRANCH_CODE", length = 3)
    var code: String? = null

    @field:Column(name = "BRANCH_NAME", nullable = false, length = 40)
    var name: String? = null

    @field:Column(name = "BRANCH_NAME_KANA", nullable = false, length = 40)
    var nameKana: String? = null
}
