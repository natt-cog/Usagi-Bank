package jp.usagi.bank.domain

/** 口座状態. */
enum class AccountStatus(val label: String) {
    ACTIVE("有効"),
    FROZEN("凍結"),
    DORMANT("休眠"),
    CLOSED("解約")
}
