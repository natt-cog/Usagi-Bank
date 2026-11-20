package jp.usagi.bank.batch;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 預金口座 日次利息積数計算 (COBOL {@code batch/cobol/UBEOD001.cbl} の Java 移行版).
 *
 * <pre>
 * 入力 : ACCOUNTS.DAT  (固定長 52 桁, 口座マスタ抽出ファイル)
 * 出力 : ACCRUED.DAT   (固定長 52 桁, 未払利息更新ファイル)
 *        UBEOD001.LOG  (処理件数レポート)
 * 処理 : 有効 (A) かつ 当座 (2) 以外の口座について
 *        日次利息 = 残高 × 年利(%) ÷ 100 ÷ 365 (銭未満切捨て) を未払利息に加算する.
 * </pre>
 *
 * GnuCOBOL の LINE SEQUENTIAL ファイルと同じ入出力になるよう, 以下を再現する.
 * <ul>
 * <li>レコードはバイト単位 (ISO-8859-1 で 1 バイト = 1 文字として扱い, 文字コード変換しない).</li>
 * <li>読込: LF 区切り, CR は除去, 52 桁未満は空白補完, 超過分は切捨て.</li>
 * <li>書込: 末尾空白を除去して LF を付加.</li>
 * <li>数値項目 (PIC 9) は桁あふれ時に上位桁を切捨て, 小数部は切捨て.</li>
 * <li>戻り値 (RETURN-CODE): 正常 0, レコード種別不正・トレーラ件数不一致 8, 入力ファイルのオープン失敗 12.</li>
 * </ul>
 * 数値項目に数字以外が入っている場合は COBOL では未定義動作 (ホストでは S0C7) のため,
 * 本クラスではその時点で処理を打ち切り戻り値 12 とする.
 */
public class DailyInterestAccrualBatch {

    public static final String INPUT_FILE = "ACCOUNTS.DAT";
    public static final String OUTPUT_FILE = "ACCRUED.DAT";
    public static final String LOG_FILE = "UBEOD001.LOG";

    public static final int RC_OK = 0;
    public static final int RC_WARNING = 8;
    public static final int RC_ERROR = 12;

    static final Charset RECORD_CHARSET = StandardCharsets.ISO_8859_1;
    static final int RECORD_LENGTH = 52;
    static final int LOG_RECORD_LENGTH = 80;

    private static final BigDecimal HUNDRED = new BigDecimal(100);
    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal(365);

    private final PrintStream console;

    private int returnCode = RC_OK;
    private long inputRecordNo;
    private String headerDate = "00000000";
    private BigDecimal readCount = BigDecimal.ZERO;
    private BigDecimal accruedCount = BigDecimal.ZERO;
    private BigDecimal skippedCount = BigDecimal.ZERO;
    private BigDecimal totalBalance = BigDecimal.ZERO;
    private BigDecimal totalAccrued = BigDecimal.ZERO;
    private BigDecimal totalDaily = BigDecimal.ZERO;

    public DailyInterestAccrualBatch(PrintStream console) {
        this.console = console;
    }

    /** 使い方: {@code java jp.usagi.bank.batch.DailyInterestAccrualBatch [作業ディレクトリ]} */
    public static void main(String[] args) throws IOException {
        Path dir = Paths.get(args.length > 0 ? args[0] : ".");
        System.exit(run(dir, System.out));
    }

    /** 作業ディレクトリの ACCOUNTS.DAT を読み, 同じディレクトリに ACCRUED.DAT と UBEOD001.LOG を出力する. */
    public static int run(Path dir, PrintStream console) throws IOException {
        Path input = dir.resolve(INPUT_FILE);
        if (Files.notExists(input)) {
            return openError(console, "35");
        }
        InputStream opened;
        try {
            if (Files.isDirectory(input)) {
                throw new IOException(input + " is a directory");
            }
            opened = Files.newInputStream(input);
        } catch (IOException e) {
            return openError(console, "37");
        }
        try (InputStream in = new BufferedInputStream(opened);
                OutputStream out = new BufferedOutputStream(Files.newOutputStream(dir.resolve(OUTPUT_FILE)));
                OutputStream log = new BufferedOutputStream(Files.newOutputStream(dir.resolve(LOG_FILE)))) {
            return new DailyInterestAccrualBatch(console).execute(in, out, log);
        }
    }

    /** OPEN INPUT 失敗 (FILE STATUS 35: ファイルなし, 37: 権限なし等). */
    private static int openError(PrintStream console, String fileStatus) {
        console.print("UBEOD001E ACCOUNTS.DAT OPEN ERROR " + fileStatus + "\n");
        console.flush();
        return RC_ERROR;
    }

    /** 1 回分のバッチ処理. インスタンスは使い捨て. */
    public int execute(InputStream accounts, OutputStream accrued, OutputStream log) throws IOException {
        try {
            String record;
            while ((record = readRecord(accounts)) != null) {
                inputRecordNo++;
                switch (record.charAt(0)) {
                case 'H':
                    header(record, accrued);
                    break;
                case 'D':
                    detail(record, accrued);
                    break;
                case 'T':
                    trailer(record, accrued);
                    break;
                default:
                    display("UBEOD001E UNKNOWN RECORD KIND: " + record.charAt(0));
                    returnCode = RC_WARNING;
                }
            }
            writeLog(log);
        } catch (InvalidNumericDataException e) {
            accrued.flush();
            display("UBEOD001E INVALID NUMERIC DATA RECORD=" + inputRecordNo + " FIELD=" + e.field
                    + " VALUE=[" + e.value + "]");
            return RC_ERROR;
        }
        accrued.flush();
        log.flush();
        display("UBEOD001I COMPLETED. READ=" + digits(readCount, 9, 0)
                + " ACCRUED=" + digits(accruedCount, 9, 0)
                + " SKIPPED=" + digits(skippedCount, 9, 0));
        return returnCode;
    }

    /** DISPLAY. 実行環境によらず LF 区切り. */
    private void display(String message) {
        console.print(message + "\n");
        console.flush();
    }

    private void header(String record, OutputStream out) throws IOException {
        headerDate = record.substring(1, 9);
        write(out, record);
    }

    private void detail(String record, OutputStream out) throws IOException {
        readCount = fit(readCount.add(BigDecimal.ONE), 9, 0);
        BigDecimal balance = numeric(record, 13, 15, 0, "BALANCE");
        totalBalance = fit(totalBalance.add(balance), 17, 0);
        BigDecimal accrued = numeric(record, 35, 17, 2, "ACCRUED");

        String result = record;
        char type = record.charAt(11);
        char status = record.charAt(12);
        if (status == 'A' && type != '2') {
            BigDecimal rate = numeric(record, 28, 7, 4, "RATE");
            BigDecimal work = fit(balance.multiply(rate).divide(HUNDRED), 15, 10);
            BigDecimal daily = fit(work.divide(DAYS_IN_YEAR, 2, BigDecimal.ROUND_DOWN), 15, 2);
            accrued = fit(accrued.add(daily), 15, 2);
            totalDaily = fit(totalDaily.add(daily), 15, 2);
            accruedCount = fit(accruedCount.add(BigDecimal.ONE), 9, 0);
            result = record.substring(0, 35) + digits(accrued, 17, 2);
        } else {
            skippedCount = fit(skippedCount.add(BigDecimal.ONE), 9, 0);
        }

        totalAccrued = fit(totalAccrued.add(accrued), 15, 2);
        write(out, result);
    }

    /** トレーラは再集計した値で出力 (入力トレーラは件数照合のみ). */
    private void trailer(String record, OutputStream out) throws IOException {
        String inCount = record.substring(1, 10);
        String read = digits(readCount, 9, 0);
        if (!inCount.equals(read)) {
            display("UBEOD001E TRAILER COUNT MISMATCH IN=" + inCount + " READ=" + read);
            returnCode = RC_WARNING;
        }
        write(out, "T" + read + digits(totalBalance, 17, 0) + digits(totalAccrued, 17, 2)
                + record.substring(44, RECORD_LENGTH));
    }

    private void writeLog(OutputStream log) throws IOException {
        BigDecimal date = numeric("H" + headerDate, 1, 8, 0, "SHORI-BI");
        writeLogLine(log, "UBEOD001 NICHIJI RISOKU SEKISUU");
        writeLogLine(log, countLine("SHORI-BI (YYYYMMDD)", date));
        writeLogLine(log, countLine("YOMIKOMI KENSUU", readCount));
        writeLogLine(log, countLine("RISOKU KEISAN KENSUU", accruedCount));
        writeLogLine(log, countLine("TAISHOUGAI KENSUU", skippedCount));
        writeLogLine(log, amountLine("TOUJITSU RISOKU GOUKEI (YEN)", totalDaily));
        writeLogLine(log, amountLine("MIBARAI RISOKU GOUKEI (YEN)", totalAccrued));
    }

    /** PIC X(30) + PIC Z(16)9. */
    private static String countLine(String label, BigDecimal value) {
        return rightPad(label, 30) + leftPad(value.toPlainString(), 17, ' ');
    }

    /** PIC X(30) + PIC Z(14)9.99. */
    private static String amountLine(String label, BigDecimal value) {
        return rightPad(label, 30) + leftPad(value.setScale(2).toPlainString(), 18, ' ');
    }

    /** LINE SEQUENTIAL の READ. LF 区切り, CR 除去, 固定長に空白補完・切捨て. EOF で null. */
    static String readRecord(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(RECORD_LENGTH + 2);
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            if (b != '\r') {
                buf.write(b);
            }
        }
        if (b == -1 && buf.size() == 0) {
            return null;
        }
        String line = new String(buf.toByteArray(), RECORD_CHARSET);
        return line.length() >= RECORD_LENGTH ? line.substring(0, RECORD_LENGTH)
                : rightPad(line, RECORD_LENGTH);
    }

    private static void write(OutputStream out, String record) throws IOException {
        writeRecord(out, record, RECORD_LENGTH);
    }

    private static void writeLogLine(OutputStream out, String record) throws IOException {
        writeRecord(out, record, LOG_RECORD_LENGTH);
    }

    /** LINE SEQUENTIAL の WRITE. 末尾空白を除去して LF を付加. */
    private static void writeRecord(OutputStream out, String record, int length) throws IOException {
        String fixed = record.length() > length ? record.substring(0, length) : record;
        int end = fixed.length();
        while (end > 0 && fixed.charAt(end - 1) == ' ') {
            end--;
        }
        out.write(fixed.substring(0, end).getBytes(RECORD_CHARSET));
        out.write('\n');
    }

    /** PIC 9(n)V9(scale) の項目を読む. */
    private static BigDecimal numeric(String record, int offset, int length, int scale, String field) {
        String raw = record.substring(offset, offset + length);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < '0' || c > '9') {
                throw new InvalidNumericDataException(field, raw);
            }
        }
        return new BigDecimal(raw).movePointLeft(scale);
    }

    /** PIC 9(intDigits)V9(scale) への格納. 上位桁あふれ・小数部ともに切捨て. */
    private static BigDecimal fit(BigDecimal value, int intDigits, int scale) {
        return value.setScale(scale, BigDecimal.ROUND_DOWN).remainder(BigDecimal.TEN.pow(intDigits));
    }

    /** PIC 9(n)V9(scale) の表示形式 (ゼロ埋め, 小数点なし). */
    private static String digits(BigDecimal value, int length, int scale) {
        String unscaled = value.setScale(scale).unscaledValue().toString();
        return leftPad(unscaled, length, '0');
    }

    private static String rightPad(String s, int length) {
        StringBuilder sb = new StringBuilder(length).append(s);
        while (sb.length() < length) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String leftPad(String s, int length, char pad) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = s.length(); i < length; i++) {
            sb.append(pad);
        }
        return sb.append(s).toString();
    }

    private static final class InvalidNumericDataException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String field;
        final String value;

        InvalidNumericDataException(String field, String value) {
            super(field + "=[" + value + "]");
            this.field = field;
            this.value = value;
        }
    }
}
