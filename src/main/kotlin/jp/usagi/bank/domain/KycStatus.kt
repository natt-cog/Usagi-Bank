package jp.usagi.bank.domain

/** 本人確認ステータス. */
enum class KycStatus(val label: String) {
    PENDING("未確認"),
    VERIFIED("確認済"),
    REJECTED("否認"),
}
