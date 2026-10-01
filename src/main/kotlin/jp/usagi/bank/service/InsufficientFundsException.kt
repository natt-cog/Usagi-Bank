package jp.usagi.bank.service

import jp.usagi.bank.domain.Account
import java.math.BigDecimal

class InsufficientFundsException(account: Account, required: BigDecimal) :
    BankingException(
        "UB-2001",
        "残高不足です: ${account.displayNo} 残高=${account.balance.toPlainString()} 必要額=${required.toPlainString()}",
    ) {

    companion object {
        private const val serialVersionUID = 1L
    }
}
