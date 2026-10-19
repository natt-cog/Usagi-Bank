package jp.usagi.bank.batch.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * batch/golden/ の全ケースを Java 版 {@link Ubeod001} に流し, COBOL で作った期待値
 * (ACCRUED.DAT・UBEOD001.LOG・stdout.txt・rc.txt) とバイト比較する.
 *
 * <p>batch/golden/golden-lib.sh と同じ手順: 空の作業ディレクトリに ACCOUNTS.DAT だけを置いて実行し,
 * 期待値ファイルがないものは「作られないこと」を確かめる. COBOL は呼ばない.
 */
@RunWith(Parameterized.class)
public class Ubeod001GoldenTest {

    private static final Path GOLDEN = Paths.get("batch", "golden");
    private static final String[] OUTPUT_FILES = {Ubeod001.OUTPUT_FILE, Ubeod001.LOG_FILE};

    @Parameters(name = "{0}")
    public static Collection<Object[]> cases() throws IOException {
        List<Object[]> cases = new ArrayList<>();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(GOLDEN, Files::isDirectory)) {
            Set<String> names = new TreeSet<>();
            for (Path d : dirs) {
                String name = d.getFileName().toString();
                if (!name.equals("work") && !name.startsWith("work-")) {
                    names.add(name);
                }
            }
            for (String name : names) {
                cases.add(new Object[] {name});
            }
        }
        if (cases.isEmpty()) {
            throw new IllegalStateException("ゴールデンケースがない: " + GOLDEN.toAbsolutePath());
        }
        return cases;
    }

    private final String name;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    public Ubeod001GoldenTest(String name) {
        this.name = name;
    }

    @Test
    public void matchesCobolGolden() throws IOException {
        Path golden = GOLDEN.resolve(name);
        Path work = tmp.newFolder(name).toPath();
        Path input = golden.resolve(Ubeod001.INPUT_FILE);
        if (Files.exists(input)) {
            Files.copy(input, work.resolve(Ubeod001.INPUT_FILE));
        }

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int rc;
        try (PrintStream out = new PrintStream(stdout, true, LineSequentialFile.CHARSET.name())) {
            rc = Ubeod001.run(work, out);
        }

        assertBytes("rc.txt", golden.resolve("rc.txt"), (rc + "\n").getBytes(StandardCharsets.US_ASCII));
        assertBytes("stdout.txt", golden.resolve("stdout.txt"), stdout.toByteArray());
        for (String f : OUTPUT_FILES) {
            Path expected = golden.resolve(f);
            Path actual = work.resolve(f);
            if (Files.exists(expected)) {
                assertTrue(name + ": " + f + " が出力されなかった", Files.exists(actual));
                assertBytes(f, expected, Files.readAllBytes(actual));
            } else {
                assertFalse(name + ": " + f + " は作られないはず", Files.exists(actual));
            }
        }
        assertEquals(name + ": 作業ディレクトリに余分なファイル (一時ファイルの消し忘れなど)",
                expectedWorkFiles(golden), list(work));
    }

    /** ISO-8859-1 は 1 バイト = 1 文字なので, 文字列の一致 = バイト一致. 不一致時は差分が読める. */
    private void assertBytes(String label, Path expected, byte[] actual) throws IOException {
        assertEquals(name + ": " + label,
                new String(Files.readAllBytes(expected), StandardCharsets.ISO_8859_1),
                new String(actual, StandardCharsets.ISO_8859_1));
    }

    private static Set<String> expectedWorkFiles(Path golden) {
        Set<String> files = new TreeSet<>();
        for (String f : new String[] {Ubeod001.INPUT_FILE, Ubeod001.OUTPUT_FILE, Ubeod001.LOG_FILE}) {
            if (Files.exists(golden.resolve(f))) {
                files.add(f);
            }
        }
        return files;
    }

    static Set<String> list(Path dir) throws IOException {
        Set<String> files = new TreeSet<>();
        try (DirectoryStream<Path> s = Files.newDirectoryStream(dir)) {
            for (Path p : s) {
                files.add(p.getFileName().toString());
            }
        }
        return files;
    }
}
