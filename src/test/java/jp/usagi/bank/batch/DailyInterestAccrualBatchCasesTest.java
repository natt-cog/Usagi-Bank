package jp.usagi.bank.batch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * 境界ケース (桁あふれ, CRLF・長短レコード, レコード種別不正, トレーラ不一致, ヘッダなし, 空ファイル, 利息対象判定) で
 * GnuCOBOL 版 UBEOD001 の出力 (src/test/resources/ubeod001/*) と 1 バイト単位で一致することを検証する.
 */
@RunWith(Parameterized.class)
public class DailyInterestAccrualBatchCasesTest {

    @Parameters(name = "{0}")
    public static List<Object[]> cases() throws IOException {
        return Ubeod001Fixtures.cases();
    }

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final Path caseDir;

    public DailyInterestAccrualBatchCasesTest(String name, Path caseDir) {
        this.caseDir = caseDir;
    }

    @Test
    public void matchesCobolOutputByteForByte() throws IOException {
        Path work = tmp.getRoot().toPath();
        Ubeod001Fixtures.copyInput(caseDir.resolve("ACCOUNTS.DAT"), work);

        Ubeod001Fixtures.Result r = Ubeod001Fixtures.runJava(work);

        assertArrayEquals("ACCRUED.DAT", Files.readAllBytes(caseDir.resolve("ACCRUED.DAT")), r.accrued);
        assertArrayEquals("UBEOD001.LOG", Files.readAllBytes(caseDir.resolve("UBEOD001.LOG")), r.log);
        assertEquals("console", Ubeod001Fixtures.text(Files.readAllBytes(caseDir.resolve("CONSOLE.TXT"))),
                Ubeod001Fixtures.text(r.console));
        assertEquals("RC", new String(Files.readAllBytes(caseDir.resolve("RC.TXT")), StandardCharsets.US_ASCII).trim(),
                String.valueOf(r.rc));
    }
}
