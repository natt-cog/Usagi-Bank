package jp.usagi.bank.batch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** UBEOD001 移行テスト用のフィクスチャ. */
final class Ubeod001Fixtures {

    static final Path COBOL_DIR = Paths.get("batch", "cobol");
    static final Path GOLDEN_INPUT = COBOL_DIR.resolve("data").resolve("ACCOUNTS.DAT");
    static final Path GOLDEN_EXPECTED = COBOL_DIR.resolve("expected");

    /** GnuCOBOL で UBEOD001 を実行して作成した期待値 (ACCOUNTS.DAT, ACCRUED.DAT, UBEOD001.LOG, CONSOLE.TXT, RC.TXT). */
    static final Path CASES_DIR = Paths.get("src", "test", "resources", "ubeod001");

    private Ubeod001Fixtures() {
    }

    static List<Object[]> cases() throws IOException {
        List<Object[]> cases = new ArrayList<Object[]>();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(CASES_DIR)) {
            for (Path dir : dirs) {
                cases.add(new Object[] { dir.getFileName().toString(), dir });
            }
        }
        Collections.sort(cases, (a, b) -> ((String) a[0]).compareTo((String) b[0]));
        return cases;
    }

    /** Java 版を workDir で実行した結果. */
    static Result runJava(Path workDir) throws IOException {
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        int rc = DailyInterestAccrualBatch.run(workDir, new PrintStream(console, true, "ISO-8859-1"));
        return new Result(workDir, rc, console.toByteArray());
    }

    static Path copyInput(Path input, Path workDir) throws IOException {
        Files.createDirectories(workDir);
        return Files.copy(input, workDir.resolve(DailyInterestAccrualBatch.INPUT_FILE));
    }

    static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    static final class Result {
        final int rc;
        final byte[] console;
        final byte[] accrued;
        final byte[] log;

        Result(Path workDir, int rc, byte[] console) throws IOException {
            this.rc = rc;
            this.console = console;
            this.accrued = Files.readAllBytes(workDir.resolve(DailyInterestAccrualBatch.OUTPUT_FILE));
            this.log = Files.readAllBytes(workDir.resolve(DailyInterestAccrualBatch.LOG_FILE));
        }
    }
}
