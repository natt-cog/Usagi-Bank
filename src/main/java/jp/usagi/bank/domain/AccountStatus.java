package jp.usagi.bank.domain;

/** 口座状態. */
public enum AccountStatus {
    ACTIVE("有効"),
    FROZEN("凍結"),
    DORMANT("休眠"),
    CLOSED("解約");

    private final String label;

    AccountStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
