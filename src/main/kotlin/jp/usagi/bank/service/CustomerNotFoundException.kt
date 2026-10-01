package jp.usagi.bank.service

class CustomerNotFoundException(
    cifNo: String?,
) : BankingException("UB-1003", "顧客が存在しません: CIF=$cifNo") {
    companion object {
        private const val serialVersionUID = 1L
    }
}
