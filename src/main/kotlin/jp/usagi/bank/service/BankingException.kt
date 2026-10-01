package jp.usagi.bank.service

/** 業務例外の基底クラス. エラーコードは勘定系共通コード表 (UB-xxxx) に準拠. */
open class BankingException(val errorCode: String, message: String) : RuntimeException(message) {

    companion object {
        private const val serialVersionUID = 1L
    }
}
