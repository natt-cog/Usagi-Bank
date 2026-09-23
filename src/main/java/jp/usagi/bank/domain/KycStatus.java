package jp.usagi.bank.domain;

/** 本人確認ステータス. */
public enum KycStatus {
    PENDING("未確認"),
    VERIFIED("確認済"),
    REJECTED("否認");

    private final String label;

    KycStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
