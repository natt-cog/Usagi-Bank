package jp.usagi.bank.batch.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Java 版だけのエラー (設計メモ §7.2, RC=12) と, ゴールデンにない入力の期待値.
 * COBOL では結果を再現できない入力なので, 期待値は設計メモ §7.2 から直接書く.
 */
public class Ubeod001Test {

    private static final String H = "H20180315";
    /** 仕様書 付録 A の明細 G1 (状態 A・種目 1 の利息対象). */
    private static final String G1 = "D00110000011A000000001250000000001000000000000001234";
    private static final String T1 = "T000000001";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path dir;
    private String stdout;

    @Before
    public void setUp() throws IOException {
        dir = tmp.newFolder("work").toPath();
    }

    /** base の項目 f を value (空白で埋めて項目長にそろえる) に置き換えた 52 桁のレコード. */
    private static String with(String base, Field f, String value) {
        StringBuilder rec = new StringBuilder(base);
        while (rec.length() < HostRecord.LENGTH) {
            rec.append(' ');
        }
        StringBuilder v = new StringBuilder(value);
        while (v.length() < f.length()) {
            v.append(' ');
        }
        return rec.replace(f.offset(), f.end(), v.toString()).toString();
    }

    private void input(String... lines) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        Files.write(dir.resolve(Ubeod001.INPUT_FILE), sb.toString().getBytes(LineSequentialFile.CHARSET));
    }

    private int run() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int rc;
        try (PrintStream out = new PrintStream(bos, true, LineSequentialFile.CHARSET.name())) {
            rc = Ubeod001.run(dir, out);
        }
        stdout = new String(bos.toByteArray(), LineSequentialFile.CHARSET);
        return rc;
    }

    /** RC=12・stdout がこのとおり・ACCRUED.DAT と UBEOD001.LOG (一時ファイルも) を残さない. */
    private void assertAbend(String... messages) throws IOException {
        assertAbendLeaving(new String[0], messages);
    }

    private void assertAbendLeaving(String[] otherFiles, String... messages) throws IOException {
        assertEquals(12, run());
        StringBuilder expected = new StringBuilder();
        for (String m : messages) {
            expected.append(m).append('\n');
        }
        assertEquals(expected.toString(), stdout);
        Set<String> files = new TreeSet<>(Arrays.asList(otherFiles));
        if (Files.exists(dir.resolve(Ubeod001.INPUT_FILE))) {
            files.add(Ubeod001.INPUT_FILE);
        }
        assertEquals(files, Ubeod001GoldenTest.list(dir));
    }

    // ---- 数値項目に数字以外 (仕様書 E17 後半・E18) ----

    /** E17 後半: 29 桁の明細 (状態 A・種目 1). 30 桁目以降が空白で埋まり, 利率が数字以外. */
    @Test
    public void e17ShortDetailIsNotNumericRate() throws IOException {
        input(H, G1 + "XYZ", "D00110000011A0000000012500000", "T000000002");
        assertAbend("UBEOD001E NOT NUMERIC DTL-RATE RECORD=000000003");
    }

    /** E18: 残高が空白. */
    @Test
    public void e18BlankBalance() throws IOException {
        input(H, "D00110000011A               0010000000000000000001234", T1);
        assertAbend("UBEOD001E NOT NUMERIC DTL-BALANCE RECORD=000000002");
    }

    @Test
    public void headerDateNotNumeric() throws IOException {
        input("H2018031A", G1, T1);
        assertAbend("UBEOD001E NOT NUMERIC HDR-DATE RECORD=000000001");
    }

    /** 1 つの明細で複数の項目が数字以外なら, COBOL が使う順 (残高 → 利率 → 未払利息) の最初. */
    @Test
    public void firstBadFieldInCobolOrder() throws IOException {
        String allBad = with(with(with(G1, Field.DTL_BALANCE, "X"), Field.DTL_RATE, "X"), Field.DTL_ACCRUED, "X");
        input(H, allBad, T1);
        assertAbend("UBEOD001E NOT NUMERIC DTL-BALANCE RECORD=000000002");
    }

    @Test
    public void rateCheckedBeforeAccrued() throws IOException {
        input(H, with(with(G1, Field.DTL_RATE, "1.0000"), Field.DTL_ACCRUED, "-1"), T1);
        assertAbend("UBEOD001E NOT NUMERIC DTL-RATE RECORD=000000002");
    }

    @Test
    public void accruedNotNumericOnInterestRecord() throws IOException {
        input(H, with(G1, Field.DTL_ACCRUED, "00000000000001234"), with(G1, Field.DTL_ACCRUED, "+0000000000001234"),
                "T000000002");
        assertAbend("UBEOD001E NOT NUMERIC DTL-ACCRUED RECORD=000000003");
    }

    /** 利息対象外 (状態 F) でも残高と未払利息は合計に足すので検査する. */
    @Test
    public void balanceAndAccruedCheckedOnSkippedRecord() throws IOException {
        String skipped = with(G1, Field.DTL_STATUS, "F");
        input(H, with(skipped, Field.DTL_ACCRUED, "0000000000000123 "), T1);
        assertAbend("UBEOD001E NOT NUMERIC DTL-ACCRUED RECORD=000000002");

        input(H, with(skipped, Field.DTL_BALANCE, "00000000125000O"), T1);
        assertAbend("UBEOD001E NOT NUMERIC DTL-BALANCE RECORD=000000002");
    }

    /** 利息対象外の明細の利率は使わないので検査せず, バイトのまま出力する (設計メモ X08, COBOL と同じ). */
    @Test
    public void rateNotCheckedOnSkippedRecord() throws IOException {
        String current = with(with(G1, Field.DTL_TYPE, "2"), Field.DTL_RATE, "ABC 1.0");
        input(H, current, T1);
        assertEquals(0, run());
        List<String> out = Arrays.asList(new String(Files.readAllBytes(dir.resolve(Ubeod001.OUTPUT_FILE)),
                LineSequentialFile.CHARSET).split("\n"));
        assertEquals(current, out.get(1));
        assertEquals("UBEOD001I COMPLETED. READ=000000001 ACCRUED=000000000 SKIPPED=000000001\n", stdout);
    }

    /** 全角数字は数字ではない (MS932 の "１" = 0x82 0x50). */
    @Test
    public void fullWidthDigitIsNotNumeric() throws IOException {
        input(H, with(G1, Field.DTL_BALANCE, "0000000012500\u0082\u0050"), T1);
        assertAbend("UBEOD001E NOT NUMERIC DTL-BALANCE RECORD=000000002");
    }

    // ---- トレーラ件数 (空白 9 バイトは COBOL と同じ MISMATCH・RC=8。ゴールデン trailer-count-blank) ----

    @Test
    public void trailerCountNotNumeric() throws IOException {
        input(H, G1, "T00000000A");
        assertAbend("UBEOD001E NOT NUMERIC TRL-COUNT RECORD=000000003");
    }

    /** 空白と数字が混ざった件数は「全部空白」ではないので NOT NUMERIC. */
    @Test
    public void trailerCountMixedBlankAndDigits() throws IOException {
        input(H, G1, "T       01");
        assertAbend("UBEOD001E NOT NUMERIC TRL-COUNT RECORD=000000003");

        input(H, G1, "T00000001 ");
        assertAbend("UBEOD001E NOT NUMERIC TRL-COUNT RECORD=000000003");
    }

    // ---- NUL ----

    @Test
    public void nulByteInRecord() throws IOException {
        input(H, G1.substring(0, 8) + '\0' + G1.substring(9), T1);
        assertAbend("UBEOD001E NUL BYTE RECORD=000000002");
    }

    /** 53 桁目以降 (切り捨てる部分) の NUL も RC=12. */
    @Test
    public void nulByteBeyondRecordLength() throws IOException {
        input(H, G1, T1 + pad(42) + "XX\0");
        assertAbend("UBEOD001E NUL BYTE RECORD=000000003");
    }

    // ---- RC=12 の前のメッセージ ----

    /** 先に出した UNKNOWN RECORD KIND (RC=8) は残し, RC は 12 が優先. 完了メッセージは出さない. */
    @Test
    public void messagesBeforeAbendAreKept() throws IOException {
        input(H, "X", "", G1, with(G1, Field.DTL_BALANCE, ""), "T000000002");
        assertAbend("UBEOD001E UNKNOWN RECORD KIND: X",
                "UBEOD001E UNKNOWN RECORD KIND:  ",
                "UBEOD001E NOT NUMERIC DTL-BALANCE RECORD=000000005");
    }

    // ---- 入力ファイル ----

    /** ACCOUNTS.DAT がディレクトリ: COBOL は空ファイル扱いで RC=0 (設計メモ X06). */
    @Test
    public void inputIsDirectory() throws IOException {
        Files.createDirectory(dir.resolve(Ubeod001.INPUT_FILE));
        assertAbend("UBEOD001E ACCOUNTS.DAT OPEN ERROR 30");
    }

    /** 読めない ACCOUNTS.DAT は COBOL と同じ OPEN ERROR 37 (設計メモ X06). root では読めてしまうので飛ばす. */
    @Test
    public void inputNotReadable() throws IOException {
        input(H, G1, T1);
        Path in = dir.resolve(Ubeod001.INPUT_FILE);
        Files.setPosixFilePermissions(in, PosixFilePermissions.fromString("---------"));
        assumeFalse(Files.isReadable(in));
        assertAbend("UBEOD001E ACCOUNTS.DAT OPEN ERROR 37");
    }

    // ---- 出力ファイルが作れない・置き換えられない (仕様書 §8 #4, 設計メモ X07) ----

    @Test
    public void accruedTmpNotCreatable() throws IOException {
        input(H, G1, T1);
        blockWith(Ubeod001.OUTPUT_FILE + ".tmp");
        assertAbendLeaving(new String[] {"ACCRUED.DAT.tmp"}, "UBEOD001E ACCRUED.DAT WRITE ERROR 30");
    }

    @Test
    public void logTmpNotCreatable() throws IOException {
        input(H, G1, T1);
        blockWith(Ubeod001.LOG_FILE + ".tmp");
        assertAbendLeaving(new String[] {"UBEOD001.LOG.tmp"}, "UBEOD001E UBEOD001.LOG WRITE ERROR 30");
    }

    @Test
    public void accruedNotReplaceable() throws IOException {
        input(H, G1, T1);
        blockWith(Ubeod001.OUTPUT_FILE);
        assertAbendLeaving(new String[] {"ACCRUED.DAT"}, "UBEOD001E ACCRUED.DAT WRITE ERROR 30");
        assertTrue(Files.isDirectory(dir.resolve(Ubeod001.OUTPUT_FILE)));
    }

    /** LOG の置き換えに失敗したら, 先に置き換えた ACCRUED.DAT も残さない. */
    @Test
    public void logNotReplaceable() throws IOException {
        input(H, G1, T1);
        blockWith(Ubeod001.LOG_FILE);
        assertAbendLeaving(new String[] {"UBEOD001.LOG"}, "UBEOD001E UBEOD001.LOG WRITE ERROR 30");
        assertFalse(Files.exists(dir.resolve(Ubeod001.OUTPUT_FILE)));
    }

    /** name を中身のあるディレクトリにして, ファイルとして作ることも置き換えることもできなくする. */
    private void blockWith(String name) throws IOException {
        Files.createFile(Files.createDirectory(dir.resolve(name)).resolve("keep"));
    }

    private static String pad(int n) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < n) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
