package jp.usagi.bank.batch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * GnuCOBOL (cobc) がある環境で, UBEOD001.cbl をその場でコンパイル・実行し,
 * Java 版と出力 (ACCRUED.DAT, UBEOD001.LOG, コンソール, RC) を突き合わせる. cobc がなければスキップ.
 */
public class Ubeod001CobolDifferentialTest {

    @ClassRule
    public static TemporaryFolder build = new TemporaryFolder();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static File cobolExe;

    @BeforeClass
    public static void compileCobol() throws Exception {
        assumeTrue("cobc not found", exec(null, "cobc", "--version").rc == 0);
        cobolExe = new File(build.getRoot(), "ubeod001");
        Exec compile = exec(null, "cobc", "-x", "-o", cobolExe.getPath(),
                Ubeod001Fixtures.COBOL_DIR.resolve("UBEOD001.cbl").toString());
        assertEquals(Ubeod001Fixtures.text(compile.stdout), 0, compile.rc);
    }

    @Test
    public void goldenInput() throws Exception {
        assertSameAsCobol(Ubeod001Fixtures.GOLDEN_INPUT);
    }

    @Test
    public void boundaryCases() throws Exception {
        for (Object[] c : Ubeod001Fixtures.cases()) {
            assertSameAsCobol(((Path) c[1]).resolve("ACCOUNTS.DAT"));
        }
    }

    private void assertSameAsCobol(Path input) throws Exception {
        Path cobolDir = Ubeod001Fixtures.copyInput(input, tmp.newFolder().toPath()).getParent();
        Path javaDir = Ubeod001Fixtures.copyInput(input, tmp.newFolder().toPath()).getParent();

        Exec cobol = exec(cobolDir.toFile(), cobolExe.getPath());
        Ubeod001Fixtures.Result cobolResult = new Ubeod001Fixtures.Result(cobolDir, cobol.rc, cobol.stdout);
        Ubeod001Fixtures.Result java = Ubeod001Fixtures.runJava(javaDir);

        String label = input.toString();
        assertArrayEquals(label + " ACCRUED.DAT", cobolResult.accrued, java.accrued);
        assertArrayEquals(label + " UBEOD001.LOG", cobolResult.log, java.log);
        assertEquals(label + " console", Ubeod001Fixtures.text(cobolResult.console), Ubeod001Fixtures.text(java.console));
        assertEquals(label + " RC", cobolResult.rc, java.rc);
    }

    private static Exec exec(File dir, String... command) throws Exception {
        List<String> cmd = new ArrayList<String>();
        for (String c : command) {
            cmd.add(c);
        }
        File stdout = File.createTempFile("ubeod001", ".out");
        try {
            Process p;
            try {
                p = new ProcessBuilder(cmd).directory(dir).redirectErrorStream(true)
                        .redirectOutput(stdout).start();
            } catch (IOException e) {
                return new Exec(-1, new byte[0]);
            }
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("timeout: " + cmd);
            }
            return new Exec(p.exitValue(), Files.readAllBytes(stdout.toPath()));
        } finally {
            stdout.delete();
        }
    }

    private static final class Exec {
        final int rc;
        final byte[] stdout;

        Exec(int rc, byte[] stdout) {
            this.rc = rc;
            this.stdout = stdout;
        }
    }
}
