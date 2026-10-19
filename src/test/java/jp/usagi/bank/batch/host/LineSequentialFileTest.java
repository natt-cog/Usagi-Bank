package jp.usagi.bank.batch.host;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * {@link LineSequentialFile}. 期待値は GnuCOBOL の LINE SEQUENTIAL の実際の挙動
 * (batch/golden/ のケースと設計メモ §12 X01-X04).
 */
public class LineSequentialFileTest {

    private static final Path GOLDEN = Paths.get("batch", "golden");
    private static final String D = "D00110000011A000000001250000000001000000000000001234";
    private static final String SP52 = pad("", 52);

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static String pad(String s, int n) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(LineSequentialFile.CHARSET);
    }

    private static List<String> read(byte[] in) throws IOException {
        List<String> out = new ArrayList<>();
        try (LineSequentialFile.Reader r = new LineSequentialFile.Reader(new ByteArrayInputStream(in), 52)) {
            String rec;
            while ((rec = r.read()) != null) {
                assertEquals(52, rec.length());
                out.add(rec);
            }
        }
        return out;
    }

    private static byte[] write(String... records) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (LineSequentialFile.Writer w = new LineSequentialFile.Writer(bos, 52)) {
            for (String rec : records) {
                w.write(rec);
            }
        }
        return bos.toByteArray();
    }

    @Test
    public void lfSeparatedRecordsArePadded() throws IOException {
        assertEquals(Arrays.asList(pad("H20180315", 52), D, pad("T000000001", 52)),
                read(bytes("H20180315\n" + D + "\nT000000001\n")));
    }

    /** empty-file: 0 レコード. 末尾の LF の後ろに空レコードは作らない. */
    @Test
    public void emptyFileAndFinalLf() throws IOException {
        assertEquals(0, read(new byte[0]).size());
        assertEquals(1, read(bytes(D + "\n")).size());
    }

    /** no-final-newline (E20): 最終行に LF がなくても 1 レコード. */
    @Test
    public void lastLineWithoutLf() throws IOException {
        assertEquals(Arrays.asList(D, pad("T", 52)), read(bytes(D + "\nT")));
    }

    /** blank-line (E11): 空行は 52 バイトの空白. */
    @Test
    public void blankLineIsAllSpaces() throws IOException {
        assertEquals(Arrays.asList(pad("H20180315", 52), SP52, D), read(bytes("H20180315\n\n" + D + "\n")));
    }

    /** crlf-input (E19) と X01・X02: CR は行のどこにあっても除く. CR だけの改行は区切りにならない. */
    @Test
    public void carriageReturnsAreRemovedEverywhere() throws IOException {
        assertEquals(Arrays.asList(D, D), read(bytes(D + "\r\n" + D + "\r\r\n")));
        assertEquals(Arrays.asList(D), read(bytes("D0011000\r0011A000000001250000000001000000000000001234\n")));
        assertEquals(Arrays.asList("H20180315" + D.substring(0, 43)), read(bytes("H20180315\r" + D + "\rT000000001\r")));
    }

    /** long-record (E17 前半): 53 バイト目以降は捨てる. */
    @Test
    public void longLineIsTruncated() throws IOException {
        assertEquals(Arrays.asList(D, pad("T", 52)), read(bytes(D + "0123456789\nT\n")));
    }

    /** X04: CR・LF・NUL 以外の制御文字と 0x80-0xFF はそのまま 1 バイト. */
    @Test
    public void otherBytesPassThrough() throws IOException {
        for (int b : new int[] {0x01, 0x02, 0x07, 0x08, 0x09, 0x0B, 0x0C, 0x1B, 0x1F, 0x7F, 0x82, 0xFF}) {
            String in = "D0011000" + (char) b + "011A000000001250000000001000000000000001234";
            List<String> recs = read(bytes(in + "\n"));
            assertEquals(1, recs.size());
            assertEquals(in.substring(0, 52), recs.get(0));
            assertEquals(b, recs.get(0).charAt(8));
        }
    }

    /** X03: NUL は再現の対象にしない (RC=12). */
    @Test
    public void nulByteAbends() throws IOException {
        byte[] in = bytes("H20180315\nD0011000\u00000011A000000001250000000001000000000000001234\nT\n");
        try {
            read(in);
            fail();
        } catch (Ubeod001Abend e) {
            assertEquals("UBEOD001E NUL BYTE RECORD=000000002", e.getMessage());
        }
    }

    @Test
    public void readerCountsRecords() throws IOException {
        try (LineSequentialFile.Reader r = new LineSequentialFile.Reader(
                new ByteArrayInputStream(bytes("A\n\nB")), 52)) {
            r.read();
            r.read();
            r.read();
            assertEquals(3, r.recordNo());
            assertNull(r.read());
            assertEquals(3, r.recordNo());
        }
    }

    /** 出力: 末尾の空白 (0x20) だけ落として LF. 途中の空白と TAB は残す. CR は出さない. */
    @Test
    public void writerTrimsTrailingSpacesOnly() throws IOException {
        assertArrayEquals(bytes("H20180315\n" + D + "\nT 1\t\n\n"),
                write(pad("H20180315", 52), D, pad("T 1\t", 52), SP52));
        assertArrayEquals(bytes(D + "\n"), write(D + "XYZ"));
    }

    /** LOG は 80 バイトのレコード. 1 行目は COBOL リテラル末尾の空白 2 個が落ちる. */
    @Test
    public void logLineTrimming() {
        assertEquals("UBEOD001 NICHIJI RISOKU SEKISUU",
                LineSequentialFile.toLine(pad("UBEOD001 NICHIJI RISOKU SEKISUU  ", 80), 80));
        assertEquals("SHORI-BI (YYYYMMDD)                    20180315",
                LineSequentialFile.toLine(pad(String.format("%-30s%17s", "SHORI-BI (YYYYMMDD)", "20180315"), 80), 80));
    }

    /** 全ゴールデンの ACCRUED.DAT / UBEOD001.LOG を読んで書き戻すとバイト一致する. */
    @Test
    public void goldenOutputsRoundTrip() throws IOException {
        int files = 0;
        for (File dir : goldenCases()) {
            for (String[] f : new String[][] {{"ACCRUED.DAT", "52"}, {"UBEOD001.LOG", "80"}}) {
                Path p = dir.toPath().resolve(f[0]);
                if (!Files.exists(p)) {
                    continue;
                }
                int len = Integer.parseInt(f[1]);
                byte[] expected = Files.readAllBytes(p);
                Path out = tmp.newFile().toPath();
                try (LineSequentialFile.Reader r = LineSequentialFile.openReader(p, len);
                        LineSequentialFile.Writer w = LineSequentialFile.openWriter(out, len)) {
                    String rec;
                    while ((rec = r.read()) != null) {
                        w.write(rec);
                    }
                }
                assertArrayEquals(p.toString(), expected, Files.readAllBytes(out));
                files++;
            }
        }
        assertTrue("files=" + files, files >= 40);
    }

    /**
     * 全ゴールデンの ACCOUNTS.DAT を読み, COBOL が入力のまま書き出すレコード
     * (ヘッダと利息対象外の明細) を書くと, ACCRUED.DAT の同じ位置の行と一致する.
     */
    @Test
    public void goldenPassThroughRecordsMatchCobol() throws IOException {
        int compared = 0;
        for (File dir : goldenCases()) {
            Path accrued = dir.toPath().resolve("ACCRUED.DAT");
            if (!Files.exists(accrued)) {
                continue;
            }
            List<String> in = new ArrayList<>();
            try (LineSequentialFile.Reader r = LineSequentialFile.openReader(dir.toPath().resolve("ACCOUNTS.DAT"), 52)) {
                String rec;
                while ((rec = r.read()) != null) {
                    in.add(rec);
                }
            }
            List<String> out = Arrays.asList(new String(Files.readAllBytes(accrued), LineSequentialFile.CHARSET)
                    .split("\n", -1));
            int o = 0;
            for (String rec : in) {
                HostRecord r = new HostRecord(rec, 0);
                char k = r.kind();
                if (k != 'H' && k != 'D' && k != 'T') {
                    continue;
                }
                boolean passThrough = k == 'H'
                        || (k == 'D' && !("A".equals(r.text(Field.DTL_STATUS)) && !"2".equals(r.text(Field.DTL_TYPE))));
                if (passThrough) {
                    assertEquals(dir.getName() + " line " + (o + 1), out.get(o),
                            LineSequentialFile.toLine(r.toLine(), 52));
                    compared++;
                }
                o++;
            }
            assertEquals(dir.getName(), "", out.get(out.size() - 1));
            assertEquals(dir.getName(), o, out.size() - 1);
        }
        assertTrue("compared=" + compared, compared >= 50);
    }

    private static File[] goldenCases() {
        File[] dirs = GOLDEN.toFile().listFiles(f -> f.isDirectory() && new File(f, "ACCOUNTS.DAT").exists());
        assertTrue("golden cases", dirs != null && dirs.length >= 25);
        Arrays.sort(dirs);
        return dirs;
    }
}
