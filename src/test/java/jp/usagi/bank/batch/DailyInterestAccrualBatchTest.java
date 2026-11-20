package jp.usagi.bank.batch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * UBEOD001 (COBOL) → {@link DailyInterestAccrualBatch} (Java) 移行の受入テスト.
 *
 * batch/cobol/data/ACCOUNTS.DAT を入力したときの出力が
 * batch/cobol/expected のゴールデンファイルと 1 バイト単位で一致することを検証する.
 */
public class DailyInterestAccrualBatchTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void matchesGoldenFilesByteForByte() throws IOException {
        Path work = tmp.getRoot().toPath();
        Ubeod001Fixtures.copyInput(Ubeod001Fixtures.GOLDEN_INPUT, work);

        Ubeod001Fixtures.Result r = Ubeod001Fixtures.runJava(work);

        assertArrayEquals("ACCRUED.DAT",
                Files.readAllBytes(Ubeod001Fixtures.GOLDEN_EXPECTED.resolve("ACCRUED.DAT")), r.accrued);
        assertArrayEquals("UBEOD001.LOG",
                Files.readAllBytes(Ubeod001Fixtures.GOLDEN_EXPECTED.resolve("UBEOD001.LOG")), r.log);
        assertEquals(DailyInterestAccrualBatch.RC_OK, r.rc);
        assertEquals("UBEOD001I COMPLETED. READ=000000015 ACCRUED=000000011 SKIPPED=000000004\n",
                Ubeod001Fixtures.text(r.console));
    }

    @Test
    public void missingInputEndsWithRc12AndCreatesNoOutput() throws IOException {
        Path work = tmp.getRoot().toPath();
        ByteArrayOutputStream console = new ByteArrayOutputStream();

        int rc = DailyInterestAccrualBatch.run(work, new PrintStream(console, true, "ISO-8859-1"));

        assertEquals(DailyInterestAccrualBatch.RC_ERROR, rc);
        assertEquals("UBEOD001E ACCOUNTS.DAT OPEN ERROR 35\n", Ubeod001Fixtures.text(console.toByteArray()));
        assertFalse(Files.exists(work.resolve(DailyInterestAccrualBatch.OUTPUT_FILE)));
        assertFalse(Files.exists(work.resolve(DailyInterestAccrualBatch.LOG_FILE)));
    }

    @Test
    public void unreadableInputEndsWithRc12AndCreatesNoOutput() throws IOException {
        Path work = tmp.getRoot().toPath();
        Path input = Ubeod001Fixtures.copyInput(Ubeod001Fixtures.GOLDEN_INPUT, work);
        assumeTrue("POSIX permissions required", input.toFile().setReadable(false, false));
        assumeFalse("running with read access regardless of permissions", Files.isReadable(input));
        ByteArrayOutputStream console = new ByteArrayOutputStream();

        int rc = DailyInterestAccrualBatch.run(work, new PrintStream(console, true, "ISO-8859-1"));

        assertEquals(DailyInterestAccrualBatch.RC_ERROR, rc);
        assertEquals("UBEOD001E ACCOUNTS.DAT OPEN ERROR 37\n", Ubeod001Fixtures.text(console.toByteArray()));
        assertFalse(Files.exists(work.resolve(DailyInterestAccrualBatch.OUTPUT_FILE)));
        assertFalse(Files.exists(work.resolve(DailyInterestAccrualBatch.LOG_FILE)));
    }

    @Test
    public void nonNumericBalanceAbendsWithRc12() throws IOException {
        Path work = tmp.getRoot().toPath();
        Files.write(work.resolve(DailyInterestAccrualBatch.INPUT_FILE), ("H20240101\n"
                + "D00110000011A00000000125000A000001000000000000001234\n"
                + "T000000001\n").getBytes(StandardCharsets.US_ASCII));
        ByteArrayOutputStream console = new ByteArrayOutputStream();

        int rc = DailyInterestAccrualBatch.run(work, new PrintStream(console, true, "ISO-8859-1"));

        assertEquals(DailyInterestAccrualBatch.RC_ERROR, rc);
        assertEquals("UBEOD001E INVALID NUMERIC DATA RECORD=2 FIELD=BALANCE VALUE=[00000000125000A]\n",
                Ubeod001Fixtures.text(console.toByteArray()));
    }

    @Test
    public void shortRecordIsPaddedAndThereforeRejectedWhenNumericFieldIsBlank() throws IOException {
        Path work = tmp.getRoot().toPath();
        Files.write(work.resolve(DailyInterestAccrualBatch.INPUT_FILE),
                "D0011000001\n".getBytes(StandardCharsets.US_ASCII));
        ByteArrayOutputStream console = new ByteArrayOutputStream();

        int rc = DailyInterestAccrualBatch.run(work, new PrintStream(console, true, "ISO-8859-1"));

        assertEquals(DailyInterestAccrualBatch.RC_ERROR, rc);
        assertEquals("UBEOD001E INVALID NUMERIC DATA RECORD=1 FIELD=BALANCE VALUE=[               ]\n",
                Ubeod001Fixtures.text(console.toByteArray()));
    }
}
