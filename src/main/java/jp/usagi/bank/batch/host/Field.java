package jp.usagi.bank.batch.host;

/**
 * ACCOUNTS.DAT / ACCRUED.DAT (52 桁) の項目定義. UBEOD001.cbl の WS-HDR / WS-DTL / WS-TRL に対応.
 * 開始位置は 0 始まりのバイト位置.
 */
enum Field {
    KIND("ACCT-KIND", 0, 1, null),

    HDR_DATE("WS-HDR-DATE", 1, 8, Pic.of(8, 0)),

    DTL_BRANCH("WS-DTL-BRANCH", 1, 3, null),
    DTL_ACCT_NO("WS-DTL-ACCT-NO", 4, 7, null),
    DTL_TYPE("WS-DTL-TYPE", 11, 1, null),
    DTL_STATUS("WS-DTL-STATUS", 12, 1, null),
    DTL_BALANCE("WS-DTL-BALANCE", 13, 15, Pic.of(15, 0)),
    DTL_RATE("WS-DTL-RATE", 28, 7, Pic.of(3, 4)),
    DTL_ACCRUED("WS-DTL-ACCRUED", 35, 17, Pic.of(15, 2)),

    TRL_COUNT("WS-TRL-COUNT", 1, 9, Pic.of(9, 0)),
    TRL_BALANCE("WS-TRL-BALANCE", 10, 17, Pic.of(17, 0)),
    TRL_ACCRUED("WS-TRL-ACCRUED", 27, 17, Pic.of(15, 2));

    private final String cobolName;
    private final int offset;
    private final int length;
    private final Pic pic;

    Field(String cobolName, int offset, int length, Pic pic) {
        if (pic != null && pic.length() != length) {
            throw new IllegalStateException(cobolName);
        }
        this.cobolName = cobolName;
        this.offset = offset;
        this.length = length;
        this.pic = pic;
    }

    String cobolName() {
        return cobolName;
    }

    /** メッセージ用の項目名 (WS- を除いた COBOL 名. 例: DTL-BALANCE). */
    String displayName() {
        return cobolName.startsWith("WS-") ? cobolName.substring(3) : cobolName;
    }

    int offset() {
        return offset;
    }

    int length() {
        return length;
    }

    int end() {
        return offset + length;
    }

    /** 数値項目なら PIC, 英数字項目なら null. */
    Pic pic() {
        return pic;
    }

    boolean isNumeric() {
        return pic != null;
    }
}
