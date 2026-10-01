package jp.usagi.bank.domain

/** 科目. 全銀協 預金種目コードに準拠. */
enum class AccountType(val zenginCode: String, val label: String) {
    ORDINARY("1", "普通"),
    CURRENT("2", "当座"),
    SAVINGS("4", "貯蓄"),
    TIME_DEPOSIT("9", "定期");

    companion object {
        @JvmStatic
        fun fromZenginCode(code: String): AccountType =
            values().firstOrNull { it.zenginCode == code }
                ?: throw IllegalArgumentException("不正な預金種目コード: $code")
    }
}
