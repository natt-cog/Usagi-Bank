package jp.usagi.bank.batch.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;

import org.junit.Test;

/** {@link HostRecord} / {@link Field}. レコードは batch/golden/ の入力と COBOL の出力から取った値. */
public class HostRecordTest {

    private static final String D_IN = "D00110000011A000000001250000000001000000000000001234";
    private static final String D_OUT = "D00110000011A000000001250000000001000000000000001237";

    @Test
    public void fieldsCoverAllBytesOfEachLayout() {
        assertEquals(52, Field.DTL_ACCRUED.end());
        assertEquals(Field.DTL_BRANCH.end(), Field.DTL_ACCT_NO.offset());
        assertEquals(Field.DTL_ACCT_NO.end(), Field.DTL_TYPE.offset());
        assertEquals(Field.DTL_TYPE.end(), Field.DTL_STATUS.offset());
        assertEquals(Field.DTL_STATUS.end(), Field.DTL_BALANCE.offset());
        assertEquals(Field.DTL_BALANCE.end(), Field.DTL_RATE.offset());
        assertEquals(Field.DTL_RATE.end(), Field.DTL_ACCRUED.offset());
        assertEquals(Field.TRL_COUNT.end(), Field.TRL_BALANCE.offset());
        assertEquals(Field.TRL_BALANCE.end(), Field.TRL_ACCRUED.offset());
        assertEquals(44, Field.TRL_ACCRUED.end());
        assertEquals("DTL-BALANCE", Field.DTL_BALANCE.displayName());
        assertEquals("TRL-COUNT", Field.TRL_COUNT.displayName());
    }

    @Test
    public void detailFields() {
        HostRecord r = new HostRecord(D_IN, 2);
        assertEquals('D', r.kind());
        assertEquals("001", r.text(Field.DTL_BRANCH));
        assertEquals("1000001", r.text(Field.DTL_ACCT_NO));
        assertEquals("1", r.text(Field.DTL_TYPE));
        assertEquals("A", r.text(Field.DTL_STATUS));
        assertEquals(new BigDecimal("1250000"), r.number(Field.DTL_BALANCE));
        assertEquals(new BigDecimal("0.0010"), r.number(Field.DTL_RATE));
        assertEquals(new BigDecimal("12.34"), r.number(Field.DTL_ACCRUED));
    }

    /** baseline: 未払利息だけ書き換えると COBOL の出力と同じ 52 バイトになる. */
    @Test
    public void putAccruedReproducesCobolDetail() {
        HostRecord r = new HostRecord(D_IN, 2);
        r.put(Field.DTL_ACCRUED, r.number(Field.DTL_ACCRUED).add(new BigDecimal("0.03")));
        assertEquals(D_OUT, r.toLine());
    }

    /** filler-passthrough: トレーラの 45-52 桁目 (FILLER) は入力のまま残る. */
    @Test
    public void putTrailerKeepsFiller() {
        HostRecord r = new HostRecord("T000000001                                  TRLFILL", 3);
        r.put(Field.KIND, "T")
                .put(Field.TRL_COUNT, BigDecimal.ONE)
                .put(Field.TRL_BALANCE, new BigDecimal("1250000"))
                .put(Field.TRL_ACCRUED, new BigDecimal("12.37"));
        assertEquals("T0000000010000000000125000000000000000001237TRLFILL ", r.toLine());
        assertEquals("T0000000010000000000125000000000000000001237TRLFILL",
                LineSequentialFile.toLine(r.toLine(), HostRecord.LENGTH));
    }

    /** trailer-count-blank: 'T' だけのトレーラは件数が 9 バイトとも空白. */
    @Test
    public void shortRecordIsPaddedWithSpaces() {
        HostRecord r = new HostRecord("T", 4);
        assertEquals(52, r.toLine().length());
        assertTrue(r.isBlank(Field.TRL_COUNT));
        assertEquals("         ", r.text(Field.TRL_COUNT));
        assertFalse(new HostRecord("T000000002", 4).isBlank(Field.TRL_COUNT));
    }

    /** long-record: 53 バイト目以降は捨てる. */
    @Test
    public void longRecordIsTruncated() {
        HostRecord r = new HostRecord(D_IN + "XYZ", 2);
        assertEquals(D_IN, r.toLine());
    }

    /** ms932-dbcs: 2 バイト文字はバイト単位で桁を数え, そのまま残る. */
    @Test
    public void ms932BytesAreCountedAsBytes() {
        String in = "D001\u0083A999991A000000001250000000001000000000000001234";
        HostRecord r = new HostRecord(in, 2);
        assertEquals("\u0083A99999", r.text(Field.DTL_ACCT_NO));
        assertEquals(new BigDecimal("1250000"), r.number(Field.DTL_BALANCE));
        r.put(Field.DTL_ACCRUED, new BigDecimal("12.37"));
        assertEquals("D001\u0083A999991A000000001250000000001000000000000001237", r.toLine());
    }

    @Test
    public void notNumericFieldAbendsWithFieldNameAndRecordNo() {
        assertAbend("UBEOD001E NOT NUMERIC DTL-BALANCE RECORD=000000002",
                new HostRecord("D00110000011A", 2), Field.DTL_BALANCE);
        assertAbend("UBEOD001E NOT NUMERIC DTL-RATE RECORD=000000003",
                new HostRecord("D00110000011A000000001250000ABCDEFG00000000000001234", 3), Field.DTL_RATE);
        assertAbend("UBEOD001E NOT NUMERIC TRL-COUNT RECORD=000000010",
                new HostRecord("T00000000A", 10), Field.TRL_COUNT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void numberRejectsAlphanumericField() {
        new HostRecord(D_IN, 1).number(Field.DTL_BRANCH);
    }

    private static void assertAbend(String message, HostRecord r, Field f) {
        try {
            r.number(f);
            fail("no abend for " + f);
        } catch (Ubeod001Abend e) {
            assertEquals(message, e.getMessage());
        }
    }
}
