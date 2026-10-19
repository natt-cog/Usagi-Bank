package jp.usagi.bank.batch.host;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** UBEOD001.LOG の 7 行 (3000-CLOSE の WS-LOG-LINE / WS-LOG-AMT-LINE). 1 行 80 バイト, 末尾空白は書出しで落とす. */
final class Ubeod001Log {

    static final int RECORD_LENGTH = 80;

    private static final String TITLE = "UBEOD001 NICHIJI RISOKU SEKISUU  ";

    private Ubeod001Log() {
    }

    static List<String> lines(BigDecimal hdrDate, BigDecimal readCount, BigDecimal accruedCount,
            BigDecimal skippedCount, BigDecimal totalDaily, BigDecimal totalAccrued) {
        return Arrays.asList(
                TITLE,
                countLine("SHORI-BI (YYYYMMDD)", hdrDate),
                countLine("YOMIKOMI KENSUU", readCount),
                countLine("RISOKU KEISAN KENSUU", accruedCount),
                countLine("TAISHOUGAI KENSUU", skippedCount),
                amountLine("TOUJITSU RISOKU GOUKEI (YEN)", totalDaily),
                amountLine("MIBARAI RISOKU GOUKEI (YEN)", totalAccrued));
    }

    /** WS-LOG-LABEL X(30) + WS-LOG-VALUE Z(16)9 + FILLER X(33). */
    private static String countLine(String label, BigDecimal value) {
        return String.format("%-30s%17s", label, Pic.editZ(value, 16, 0));
    }

    /** WS-LOG-AMT-LABEL X(30) + WS-LOG-AMT-VALUE Z(14)9.99 + FILLER X(32). */
    private static String amountLine(String label, BigDecimal amount) {
        return String.format("%-30s%18s", label, Pic.editZ(amount, 14, 2));
    }
}
