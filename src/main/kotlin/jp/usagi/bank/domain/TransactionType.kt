package jp.usagi.bank.domain

/** 取引種別. */
enum class TransactionType(
    val label: String,
    /** 入金側 (残高増) であれば true. */
    val isCredit: Boolean
) {
    DEPOSIT("入金", true),
    WITHDRAWAL("出金", false),
    TRANSFER_IN("振込入金", true),
    TRANSFER_OUT("振込出金", false),
    TRANSFER_FEE("振込手数料", false),
    INTEREST("利息", true),
    TAX("税金", false)
}
