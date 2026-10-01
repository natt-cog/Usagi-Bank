package jp.usagi.bank.service

class InvalidAmountException(
    message: String?,
) : BankingException("UB-3001", message) {
    companion object {
        private const val serialVersionUID = 1L
    }
}
