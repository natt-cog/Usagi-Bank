package jp.usagi.bank.batch.host;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import jp.usagi.bank.service.InterestService;

/**
 * UBEOD001 預金口座 日次利息積数計算 (batch/cobol/UBEOD001.cbl の Java 版).
 *
 * <p>カレントディレクトリの ACCOUNTS.DAT を読み, ACCRUED.DAT と UBEOD001.LOG を書き,
 * DISPLAY 相当のメッセージを stdout に出して RC (0 / 8 / 12) を終了コードで返す.
 * メソッド名の番号は COBOL の段落番号. 設計: docs/migration/UBEOD001-java-design.md
 */
public final class Ubeod001 {

    static final String INPUT_FILE = "ACCOUNTS.DAT";
    static final String OUTPUT_FILE = "ACCRUED.DAT";
    static final String LOG_FILE = "UBEOD001.LOG";

    static final int RC_WARNING = 8;
    static final int RC_ABEND = 12;

    private static final String TMP_SUFFIX = ".tmp";

    private static final Pic COUNT = Pic.of(9, 0);
    private static final Pic TOTAL_BALANCE = Pic.of(17, 0);
    private static final Pic AMOUNT = Pic.of(15, 2);
    private static final Pic WORK = Pic.of(15, 10);

    private final Path dir;
    private final PrintStream out;

    private LineSequentialFile.Reader acctFile;
    private OutputFile outFile;
    private OutputFile logFile;

    private int returnCode;

    private BigDecimal hdrDate = BigDecimal.ZERO;
    private BigDecimal readCount = BigDecimal.ZERO;
    private BigDecimal accruedCount = BigDecimal.ZERO;
    private BigDecimal skippedCount = BigDecimal.ZERO;
    private BigDecimal totalBalance = BigDecimal.ZERO;
    private BigDecimal totalAccrued = AMOUNT.store(BigDecimal.ZERO);
    private BigDecimal totalDaily = AMOUNT.store(BigDecimal.ZERO);

    private Ubeod001(Path dir, PrintStream out) {
        this.dir = dir;
        this.out = out;
    }

    public static void main(String[] args) throws UnsupportedEncodingException {
        PrintStream stdout = new PrintStream(new FileOutputStream(FileDescriptor.out), true,
                LineSequentialFile.CHARSET.name());
        int rc = run(Paths.get(""), stdout);
        stdout.flush();
        System.exit(rc);
    }

    /**
     * dir を作業ディレクトリとして UBEOD001 を実行する.
     *
     * @param out DISPLAY の出力先 (ISO-8859-1 で入力のバイトをそのまま出すこと)
     * @return RC (0 / 8 / 12)
     */
    public static int run(Path dir, PrintStream out) {
        return new Ubeod001(dir, out).main0000();
    }

    private int main0000() {
        try {
            try {
                open1000();
                String line;
                while ((line = read2100()) != null) {
                    process2000(new HostRecord(line, acctFile.recordNo()));
                }
                close3000();
            } finally {
                cleanUp();
            }
            return returnCode;
        } catch (Ubeod001Abend e) {
            display(e.getMessage());
            return RC_ABEND;
        } catch (IOException | RuntimeException e) {
            display("UBEOD001E ABEND " + e.getClass().getName());
            e.printStackTrace();
            return RC_ABEND;
        }
    }

    private void open1000() {
        Path input = dir.resolve(INPUT_FILE);
        if (Files.isDirectory(input)) {
            throw openError("30");
        }
        try {
            acctFile = LineSequentialFile.openReader(input, HostRecord.LENGTH);
        } catch (NoSuchFileException e) {
            throw openError("35");
        } catch (AccessDeniedException e) {
            throw openError("37");
        } catch (IOException e) {
            throw openError("30");
        }
        outFile = OutputFile.open(dir, OUTPUT_FILE, HostRecord.LENGTH);
        logFile = OutputFile.open(dir, LOG_FILE, Ubeod001Log.RECORD_LENGTH);
    }

    private static Ubeod001Abend openError(String status) {
        return new Ubeod001Abend("UBEOD001E " + INPUT_FILE + " OPEN ERROR " + status);
    }

    private void process2000(HostRecord rec) {
        switch (rec.kind()) {
            case 'H':
                header2200(rec);
                break;
            case 'D':
                detail2300(rec);
                break;
            case 'T':
                trailer2400(rec);
                break;
            default:
                display("UBEOD001E UNKNOWN RECORD KIND: " + rec.kind());
                returnCode = Math.max(returnCode, RC_WARNING);
                break;
        }
    }

    private String read2100() throws IOException {
        return acctFile.read();
    }

    private void header2200(HostRecord rec) {
        hdrDate = rec.number(Field.HDR_DATE);
        outFile.write(rec.toLine());
    }

    private void detail2300(HostRecord rec) {
        BigDecimal balance = rec.number(Field.DTL_BALANCE);
        readCount = COUNT.store(readCount.add(BigDecimal.ONE));
        totalBalance = TOTAL_BALANCE.store(totalBalance.add(balance));

        BigDecimal accrued;
        if (bearsInterest(rec)) {
            BigDecimal rate = rec.number(Field.DTL_RATE);
            accrued = rec.number(Field.DTL_ACCRUED);
            BigDecimal daily = AMOUNT.store(InterestService.dailyInterest(balance, rate, WORK::store));
            accrued = AMOUNT.store(accrued.add(daily));
            rec.put(Field.DTL_ACCRUED, accrued);
            totalDaily = AMOUNT.store(totalDaily.add(daily));
            accruedCount = COUNT.store(accruedCount.add(BigDecimal.ONE));
        } else {
            accrued = rec.number(Field.DTL_ACCRUED);
            skippedCount = COUNT.store(skippedCount.add(BigDecimal.ONE));
        }

        totalAccrued = AMOUNT.store(totalAccrued.add(accrued));
        outFile.write(rec.toLine());
    }

    /** 利息対象: 有効口座 (状態 A) かつ当座 (種目 2) 以外. COBOL と同じくバイトで比べる. */
    private static boolean bearsInterest(HostRecord rec) {
        return rec.text(Field.DTL_STATUS).equals("A") && !rec.text(Field.DTL_TYPE).equals("2");
    }

    /** トレーラは再集計した値で出力する (入力トレーラは件数照合のみ). */
    private void trailer2400(HostRecord rec) {
        String inCount = rec.text(Field.TRL_COUNT);
        if (rec.isBlank(Field.TRL_COUNT) || rec.number(Field.TRL_COUNT).compareTo(readCount) != 0) {
            display("UBEOD001E TRAILER COUNT MISMATCH IN=" + inCount + " READ=" + COUNT.zoned(readCount));
            returnCode = Math.max(returnCode, RC_WARNING);
        }
        rec.put(Field.KIND, "T")
                .put(Field.TRL_COUNT, readCount)
                .put(Field.TRL_BALANCE, totalBalance)
                .put(Field.TRL_ACCRUED, totalAccrued);
        outFile.write(rec.toLine());
    }

    private void close3000() throws IOException {
        for (String line : Ubeod001Log.lines(hdrDate, readCount, accruedCount, skippedCount,
                totalDaily, totalAccrued)) {
            logFile.write(line);
        }
        acctFile.close();
        outFile.close();
        logFile.close();
        outFile.commit();
        logFile.commit();
        display("UBEOD001I COMPLETED. READ=" + COUNT.zoned(readCount)
                + " ACCRUED=" + COUNT.zoned(accruedCount)
                + " SKIPPED=" + COUNT.zoned(skippedCount));
    }

    /** 閉じていないファイルを閉じ, 置き換えていない一時ファイルを消す (RC=12 で途中の出力を残さない). */
    private void cleanUp() {
        if (acctFile != null) {
            try {
                acctFile.close();
            } catch (IOException e) {
                // 入力は読み終わっているか異常終了の途中なので無視する
            }
        }
        if (outFile != null) {
            outFile.discard();
        }
        if (logFile != null) {
            logFile.discard();
        }
    }

    private void display(String message) {
        out.print(message + "\n");
        out.flush();
    }

    /** 一時ファイルに書き, commit で本来の名前に置き換える出力ファイル. 失敗は WRITE ERROR 30 (RC=12). */
    private static final class OutputFile {

        private final String name;
        private final Path target;
        private final Path tmp;
        private LineSequentialFile.Writer writer;

        private OutputFile(String name, Path target, Path tmp) {
            this.name = name;
            this.target = target;
            this.tmp = tmp;
        }

        static OutputFile open(Path dir, String name, int recordLength) {
            OutputFile f = new OutputFile(name, dir.resolve(name), dir.resolve(name + TMP_SUFFIX));
            try {
                f.writer = LineSequentialFile.openWriter(f.tmp, recordLength);
            } catch (IOException e) {
                throw f.writeError(e);
            }
            return f;
        }

        void write(String record) {
            try {
                writer.write(record);
            } catch (IOException e) {
                throw writeError(e);
            }
        }

        void close() {
            try {
                writer.close();
                writer = null;
            } catch (IOException e) {
                throw writeError(e);
            }
        }

        void commit() {
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                throw writeError(e);
            }
        }

        void discard() {
            try {
                if (writer != null) {
                    writer.close();
                    writer = null;
                }
            } catch (IOException e) {
                // 一時ファイルは下で消す
            }
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException e) {
                // 消せなくても本来のファイル名には影響しない
            }
        }

        private Ubeod001Abend writeError(IOException cause) {
            return new Ubeod001Abend("UBEOD001E " + name + " WRITE ERROR 30", cause);
        }
    }
}
