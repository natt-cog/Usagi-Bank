package jp.usagi.bank.service

import jp.usagi.bank.domain.Account

class AccountNotActiveException(account: Account) :
    BankingException(
        "UB-1002",
        "口座が取引可能な状態ではありません: ${account.displayNo} (${account.status.label})"
    ) {

    companion object {
        private const val serialVersionUID = 1L
    }
}
