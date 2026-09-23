package jp.usagi.bank.domain;

/** 科目. 全銀協 預金種目コードに準拠. */
public enum AccountType {
    ORDINARY("1", "普通"),
    CURRENT("2", "当座"),
    SAVINGS("4", "貯蓄"),
    TIME_DEPOSIT("9", "定期");

    private final String zenginCode;
    private final String label;

    AccountType(String zenginCode, String label) {
        this.zenginCode = zenginCode;
        this.label = label;
    }

    public String getZenginCode() {
        return zenginCode;
    }

    public String getLabel() {
        return label;
    }

    public static AccountType fromZenginCode(String code) {
        for (AccountType t : values()) {
            if (t.zenginCode.equals(code)) {
                return t;
            }
        }
        throw new IllegalArgumentException("不正な預金種目コード: " + code);
    }
}
