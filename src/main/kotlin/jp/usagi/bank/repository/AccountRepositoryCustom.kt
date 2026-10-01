package jp.usagi.bank.repository

import java.math.BigDecimal
import jp.usagi.bank.domain.Account
import jp.usagi.bank.domain.AccountStatus
import jp.usagi.bank.domain.AccountType

/** 動的検索 (口座検索画面用). */
interface AccountRepositoryCustom {

    fun search(
        branchCode: String?,
        type: AccountType?,
        status: AccountStatus?,
        minBalance: BigDecimal?,
        maxBalance: BigDecimal?,
        maxResults: Int,
    ): List<Account>
}
