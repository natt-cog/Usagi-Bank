package jp.usagi.bank.batch.host;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * 符号なし数値項目 PIC 9(n)V9(m) の定義と, COBOL の格納・編集規則.
 *
 * <p>格納 ({@link #store}) は ROUNDED も ON SIZE ERROR もない MOVE / ADD / COMPUTE と同じく,
 * 小数部を切り捨て, 整数部の上位桁を落とす (桁あふれでもエラーにしない).
 */
final class Pic {

    private final int integerDigits;
    private final int scale;
    private final BigDecimal modulus;

    private Pic(int integerDigits, int scale) {
        if (integerDigits < 1 || scale < 0) {
            throw new IllegalArgumentException("9(" + integerDigits + ")V9(" + scale + ")");
        }
        this.integerDigits = integerDigits;
        this.scale = scale;
        this.modulus = BigDecimal.TEN.pow(integerDigits);
    }

    /** PIC 9(n)V9(m). m = 0 なら 9(n). */
    static Pic of(int integerDigits, int scale) {
        return new Pic(integerDigits, scale);
    }

    int integerDigits() {
        return integerDigits;
    }

    int scale() {
        return scale;
    }

    /** レコード上の桁数 (n + m). */
    int length() {
        return integerDigits + scale;
    }

    /** n + m バイトが全部 '0'-'9' か. 空白・符号・小数点・全角数字は数字以外. */
    boolean isNumeric(String digits) {
        if (digits.length() != length()) {
            return false;
        }
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /** 表示形式の数字列を値にする. 数字以外は {@link IllegalArgumentException}. */
    BigDecimal parse(String digits) {
        if (!isNumeric(digits)) {
            throw new IllegalArgumentException("not numeric for " + this + ": [" + digits + "]");
        }
        return new BigDecimal(new BigInteger(digits), scale);
    }

    /** この項目に格納した値. 小数部は m 桁で切捨て, 整数部は下 n 桁だけ残す. */
    BigDecimal store(BigDecimal value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("unsigned PIC: " + value);
        }
        return value.setScale(scale, RoundingMode.DOWN).remainder(modulus);
    }

    /** 格納した値を n + m 桁のゼロ埋め数字列にする (レコードへの書き出し). */
    String zoned(BigDecimal value) {
        String digits = store(value).unscaledValue().toString();
        return pad(digits, length(), '0');
    }

    /**
     * 編集 PIC Z(z)9 (scale = 0) / Z(z)9.99 (scale = 2) の書式化.
     * 先頭のゼロは空白にし, 整数部の最下位桁は 0 でも残す. 収まらない上位桁は MOVE と同じく落とす.
     */
    static String editZ(BigDecimal value, int z, int scale) {
        BigDecimal stored = of(z + 1, scale).store(value);
        String text = scale == 0 ? stored.toBigInteger().toString() : stored.toPlainString();
        return pad(text, z + 1 + (scale == 0 ? 0 : scale + 1), ' ');
    }

    private static String pad(String s, int width, char c) {
        StringBuilder sb = new StringBuilder(width);
        for (int i = s.length(); i < width; i++) {
            sb.append(c);
        }
        return sb.append(s).toString();
    }

    @Override
    public String toString() {
        return scale == 0 ? "9(" + integerDigits + ")" : "9(" + integerDigits + ")V9(" + scale + ")";
    }
}
