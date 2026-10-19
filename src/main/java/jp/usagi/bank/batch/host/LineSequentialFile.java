package jp.usagi.bank.batch.host;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * GnuCOBOL の ORGANIZATION IS LINE SEQUENTIAL と同じ読み書き.
 *
 * <ul>
 * <li>文字コードは変換しない. バイトを ISO-8859-1 で 1 バイト = 1 文字として扱う
 *     (連携仕様上は MS932. 2 バイト文字もバイト単位で桁を数える)</li>
 * <li>読込: LF で区切り, CR は行のどこにあっても除く. 最終行の LF はなくてもよい.
 *     レコード長に満たなければ空白で埋め, 超えれば切り捨てる. NUL は RC=12</li>
 * <li>書出し: レコード長にそろえ, 末尾の空白 (0x20) を落として LF を付ける</li>
 * </ul>
 */
final class LineSequentialFile {

    static final Charset CHARSET = StandardCharsets.ISO_8859_1;

    private static final int LF = 0x0A;
    private static final int CR = 0x0D;
    private static final int NUL = 0x00;

    private LineSequentialFile() {
    }

    static Reader openReader(Path path, int recordLength) throws IOException {
        return new Reader(Files.newInputStream(path), recordLength);
    }

    static Writer openWriter(Path path, int recordLength) throws IOException {
        return new Writer(Files.newOutputStream(path), recordLength);
    }

    /** 1 レコードずつ読む. 入力の大きさに依存しないよう 1 バイトずつ組み立てる. */
    static final class Reader implements Closeable {

        private final InputStream in;
        private final int recordLength;
        private long recordNo;

        Reader(InputStream in, int recordLength) {
            this.in = new BufferedInputStream(in);
            this.recordLength = recordLength;
        }

        /** 次のレコード (recordLength バイト). 終わりなら null. NUL を含む行は {@link Ubeod001Abend}. */
        String read() throws IOException {
            byte[] buf = new byte[recordLength];
            Arrays.fill(buf, (byte) ' ');
            int len = 0;
            boolean any = false;
            boolean nul = false;
            int b;
            while ((b = in.read()) != -1) {
                any = true;
                if (b == LF) {
                    break;
                }
                if (b == CR) {
                    continue;
                }
                if (b == NUL) {
                    nul = true;
                }
                if (len < recordLength) {
                    buf[len] = (byte) b;
                }
                len++;
            }
            if (!any) {
                return null;
            }
            recordNo++;
            if (nul) {
                throw new Ubeod001Abend("UBEOD001E NUL BYTE RECORD=" + Ubeod001Abend.recordNo(recordNo));
            }
            return new String(buf, CHARSET);
        }

        /** 直前に読んだレコードの番号 (1 始まり). */
        long recordNo() {
            return recordNo;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    /** 1 レコードずつ書く. */
    static final class Writer implements Closeable {

        private final OutputStream out;
        private final int recordLength;

        Writer(OutputStream out, int recordLength) {
            this.out = new BufferedOutputStream(out);
            this.recordLength = recordLength;
        }

        void write(String record) throws IOException {
            out.write(toLine(record, recordLength).getBytes(CHARSET));
            out.write(LF);
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }

    /** レコード長にそろえて (埋める・切る) 末尾の空白だけを落とした 1 行 (改行なし). */
    static String toLine(String record, int recordLength) {
        int end = Math.min(record.length(), recordLength);
        while (end > 0 && record.charAt(end - 1) == ' ') {
            end--;
        }
        return record.substring(0, end);
    }
}
