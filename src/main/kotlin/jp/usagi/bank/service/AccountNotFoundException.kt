package jp.usagi.bank.service

class AccountNotFoundException(branchCode: String, accountNo: String) :
    BankingException("UB-1001", "口座が存在しません: $branchCode-$accountNo") {

    companion object {
        private const val serialVersionUID = 1L
    }
}
