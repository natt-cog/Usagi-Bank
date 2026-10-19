package jp.usagi.bank.batch.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.Test;

/**
 * {@link Pic} の格納・編集規則. 期待値は COBOL の実出力 (batch/golden/&lt;ケース&gt;/) から取った値.
 */
public class PicTest {

    private static final Pic BALANCE = Pic.of(15, 0);
    private static final Pic RATE = Pic.of(3, 4);
    private static final Pic ACCRUED = Pic.of(15, 2);
    private static final Pic WORK = Pic.of(15, 10);
    private static final Pic TOTAL_BALANCE = Pic.of(17, 0);
    private static final BigDecimal DAYS = new BigDecimal("365");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    /** UBEOD001 の COMPUTE WS-WORK / WS-DAILY-INT と同じ順で格納した日次利息. */
    private static BigDecimal daily(String balance, String rate) {
        BigDecimal work = WORK.store(BALANCE.parse(balance).multiply(RATE.parse(rate))
                .divide(HUNDRED, 10, RoundingMode.DOWN));
        return ACCRUED.store(work.divide(DAYS, 2, RoundingMode.DOWN));
    }

    @Test
    public void parseUsesImpliedDecimalPoint() {
        assertEquals(bd("0.0010"), RATE.parse("0000010"));
        assertEquals(bd("12.34"), ACCRUED.parse("00000000000001234"));
        assertEquals(bd("1250000"), BALANCE.parse("000000001250000"));
        assertEquals(bd("999999999999999.99"), ACCRUED.parse("99999999999999999"));
    }

    @Test
    public void isNumericAcceptsOnlyAsciiDigitsOfExactLength() {
        assertTrue(RATE.isNumeric("0000010"));
        assertFalse(RATE.isNumeric("000001"));
        assertFalse(RATE.isNumeric("00000100"));
        assertFalse(RATE.isNumeric("000001 "));
        assertFalse(RATE.isNumeric("       "));
        assertFalse(RATE.isNumeric("+000010"));
        assertFalse(RATE.isNumeric("000.010"));
        assertFalse(RATE.isNumeric("ABCDEFG"));
        assertFalse(RATE.isNumeric("00000\u00821\u0082"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseRejectsNonNumeric() {
        BALANCE.parse("               ");
    }

    @Test
    public void storeTruncatesFraction() {
        assertEquals(bd("0.03"), ACCRUED.store(bd("0.0342465753")));
        assertEquals(bd("0.03"), daily("000000001250000", "0000010"));
    }

    /** baseline: 口座 0000011 / 0000029 の日次利息 (12.34 → 12.37, 410.95 → 413.68). */
    @Test
    public void dailyInterestMatchesBaseline() {
        assertEquals(bd("12.37"), ACCRUED.store(bd("12.34").add(daily("000000001250000", "0000010"))));
        assertEquals(bd("413.68"), ACCRUED.store(bd("410.95").add(daily("000000005000000", "0000200"))));
    }

    /** max-digits (仕様書 E03-E05): WS-WORK 9(15)V9(10) で上位桁が落ちた値. */
    @Test
    public void workOverflowDropsHighDigitsLikeMaxDigitsGolden() {
        assertEquals(bd("2739723287671.20"), daily("999999999999999", "9999999"));
        assertEquals(bd("2739725479452.05"), daily("200000000000000", "9999999"));
        assertEquals(bd("273972602739.72"), daily("100000000000000", "1000000"));
        assertEquals(bd("27397260.27"), daily("999999999999999", "0000010"));
    }

    /** accrued-overflow (E06): 999999999999999.99 + 10.00 → 9.99. */
    @Test
    public void accruedOverflowDropsHighDigits() {
        BigDecimal daily = daily("000000000365000", "0010000");
        assertEquals(bd("10.00"), daily);
        assertEquals(bd("9.99"), ACCRUED.store(ACCRUED.parse("99999999999999999").add(daily)));
        assertEquals("00000000000000999", ACCRUED.zoned(ACCRUED.parse("99999999999999999").add(daily)));
    }

    /** total-balance-overflow (E07): 999999999999999 × 101 件の合計が 9(17) で切られる. */
    @Test
    public void totalBalanceOverflowMatchesTrailer() {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < 101; i++) {
            total = TOTAL_BALANCE.store(total.add(BALANCE.parse("999999999999999")));
        }
        assertEquals(bd("999999999999899"), total);
        assertEquals("00999999999999899", TOTAL_BALANCE.zoned(total));
    }

    /** total-accrued-overflow (E08): 999999999999999.99 × 2 → 999999999999999.98. */
    @Test
    public void totalAccruedOverflowMatchesTrailer() {
        BigDecimal v = ACCRUED.parse("99999999999999999");
        BigDecimal total = ACCRUED.store(ACCRUED.store(BigDecimal.ZERO.add(v)).add(v));
        assertEquals(bd("999999999999999.98"), total);
        assertEquals("99999999999999998", ACCRUED.zoned(total));
    }

    @Test
    public void zonedPadsWithZeros() {
        assertEquals("000000002", Pic.of(9, 0).zoned(bd("2")));
        assertEquals("00000000000042605", ACCRUED.zoned(bd("426.05")));
        assertEquals("00000000000625000", TOTAL_BALANCE.zoned(bd("625000")));
        assertEquals("00000000000000000", ACCRUED.zoned(BigDecimal.ZERO));
        assertEquals("0000010", RATE.zoned(bd("0.001")));
    }

    /** UBEOD001.LOG の Z(16)9 (baseline / empty-file / total-balance-overflow). */
    @Test
    public void editZCount() {
        assertEquals("         20180315", Pic.editZ(bd("20180315"), 16, 0));
        assertEquals("               15", Pic.editZ(bd("15"), 16, 0));
        assertEquals("              101", Pic.editZ(bd("101"), 16, 0));
        assertEquals("                0", Pic.editZ(BigDecimal.ZERO, 16, 0));
        assertEquals(17, Pic.editZ(bd("999999999"), 16, 0).length());
    }

    /** UBEOD001.LOG の Z(14)9.99 (baseline / max-digits / total-accrued-overflow / empty-file). */
    @Test
    public void editZAmount() {
        assertEquals("             23.76", Pic.editZ(bd("23.76"), 14, 2));
        assertEquals("           5744.01", Pic.editZ(bd("5744.01"), 14, 2));
        assertEquals("  5753448767123.24", Pic.editZ(bd("5753448767123.24"), 14, 2));
        assertEquals("999999999999999.98", Pic.editZ(bd("999999999999999.98"), 14, 2));
        assertEquals("              0.00", Pic.editZ(BigDecimal.ZERO, 14, 2));
        assertEquals("              0.03", Pic.editZ(bd("0.03"), 14, 2));
        assertEquals("              9.99", Pic.editZ(bd("9.99"), 14, 2));
    }

    @Test
    public void editZDropsHighDigitsLikeMove() {
        assertEquals("              1.00", Pic.editZ(bd("1000000000000001.00"), 14, 2));
    }
}
