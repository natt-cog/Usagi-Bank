package jp.usagi.bank.batch.host;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * 52 桁の固定長レコード (ACCT-REC). 1 バイト = 1 文字 (ISO-8859-1) で持ち, 桁はバイト単位で数える.
 * 出力は入力の 52 バイトの一部を {@link #put} で書き換えて作る (FILLER は入力のまま残る).
 */
final class HostRecord {

    static final int LENGTH = 52;

    private final char[] bytes;
    private final long recordNo;

    /**
     * @param line     52 バイトに満たなければ空白で埋め, 超えれば切り捨てる
     * @param recordNo 入力の何レコード目か (1 始まり). メッセージに使う
     */
    HostRecord(String line, long recordNo) {
        this.bytes = new char[LENGTH];
        Arrays.fill(bytes, ' ');
        line.getChars(0, Math.min(line.length(), LENGTH), bytes, 0);
        this.recordNo = recordNo;
    }

    long recordNo() {
        return recordNo;
    }

    /** レコード区分 (1 バイト目). */
    char kind() {
        return bytes[0];
    }

    String text(Field f) {
        return new String(bytes, f.offset(), f.length());
    }

    boolean isBlank(Field f) {
        for (int i = f.offset(); i < f.end(); i++) {
            if (bytes[i] != ' ') {
                return false;
            }
        }
        return true;
    }

    /**
     * 数値項目の値. 数字以外が入っていれば
     * {@code UBEOD001E NOT NUMERIC <項目> RECORD=<9 桁>} の {@link Ubeod001Abend}.
     */
    BigDecimal number(Field f) {
        Pic pic = requirePic(f);
        String s = text(f);
        if (!pic.isNumeric(s)) {
            throw new Ubeod001Abend("UBEOD001E NOT NUMERIC " + f.displayName()
                    + " RECORD=" + Ubeod001Abend.recordNo(recordNo));
        }
        return pic.parse(s);
    }

    /** 英数字項目への MOVE. 短ければ右を空白で埋め, 長ければ右を切る. */
    HostRecord put(Field f, String value) {
        for (int i = 0; i < f.length(); i++) {
            bytes[f.offset() + i] = i < value.length() ? value.charAt(i) : ' ';
        }
        return this;
    }

    /** 数値項目への MOVE ({@link Pic#zoned} の規則で格納). */
    HostRecord put(Field f, BigDecimal value) {
        return put(f, requirePic(f).zoned(value));
    }

    /** 52 バイトのレコード (末尾空白は {@link LineSequentialFile} の書出しで落とす). */
    String toLine() {
        return new String(bytes);
    }

    private static Pic requirePic(Field f) {
        if (!f.isNumeric()) {
            throw new IllegalArgumentException(f + " is not numeric");
        }
        return f.pic();
    }

    @Override
    public String toString() {
        return recordNo + ":" + toLine();
    }
}
