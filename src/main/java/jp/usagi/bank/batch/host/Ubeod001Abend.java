package jp.usagi.bank.batch.host;

/** UBEOD001 を RC=12 で止めるときの例外. メッセージは stdout に出す 1 行. */
class Ubeod001Abend extends RuntimeException {

    private static final long serialVersionUID = 1L;

    Ubeod001Abend(String message) {
        super(message);
    }

    Ubeod001Abend(String message, Throwable cause) {
        super(message, cause);
    }

    /** レコード番号の 9 桁ゼロ埋め (DISPLAY の 9(09) と同じ). */
    static String recordNo(long recordNo) {
        return Pic.of(9, 0).zoned(java.math.BigDecimal.valueOf(recordNo));
    }
}
