package jp.usagi.bank.service

import java.math.BigDecimal

class TransferLimitExceededException(
    limit: BigDecimal,
) : BankingException("UB-2002", "1日あたりの振込限度額 (${limit.toPlainString()}円) を超過します") {
    companion object {
        private const val serialVersionUID = 1L
    }
}
