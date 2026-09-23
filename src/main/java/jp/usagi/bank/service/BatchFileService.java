package jp.usagi.bank.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jp.usagi.bank.domain.Account;
import jp.usagi.bank.domain.AccountStatus;
import jp.usagi.bank.repository.AccountRepository;

/**
 * ホスト連携ファイル (固定長) の入出力.
 *
 * <pre>
 * ACCOUNTS.DAT / ACCRUED.DAT レコードレイアウト (52バイト + LF, MS932):
 *   01     レコード区分   H=ヘッダ D=データ T=トレーラ
 *   02-04  店番           X(3)
 *   05-11  口座番号       X(7)
 *   12     預金種目       X(1)  全銀コード
 *   13     口座状態       X(1)  A=有効 F=凍結 D=休眠 C=解約
 *   14-28  残高           9(15)
 *   29-35  年利           9(3)V9(4)
 *   36-52  未払利息       9(15)V9(2)
 *
 * ヘッダ  : 'H' + 処理日 YYYYMMDD + FILLER
 * トレーラ: 'T' + 件数 9(9) + 残高合計 9(17) + 未払利息合計 9(17)
 * </pre>
 */
@Service
public class BatchFileService {

    public static final Charset HOST_CHARSET = Charset.forName("MS932");
    public static final int RECORD_LENGTH = 52;
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AccountRepository accountRepository;
    private final BusinessDateService businessDateService;

    public BatchFileService(AccountRepository accountRepository, BusinessDateService businessDateService) {
        this.accountRepository = accountRepository;
        this.businessDateService = businessDateService;
    }

    @Transactional(readOnly = true)
    public int exportAccounts(OutputStream out) throws IOException {
        List<Account> accounts = accountRepository.findAll();
        java.util.Collections.sort(accounts, new java.util.Comparator<Account>() {
            @Override
            public int compare(Account a, Account b) {
                return a.getDisplayNo().compareTo(b.getDisplayNo());
            }
        });
        Writer w = new OutputStreamWriter(out, HOST_CHARSET);
        LocalDate today = businessDateService.today();
        w.write(StringUtils.rightPad("H" + today.format(YYYYMMDD), RECORD_LENGTH));
        w.write('\n');
        BigDecimal balanceTotal = BigDecimal.ZERO;
        BigDecimal accruedTotal = BigDecimal.ZERO;
        for (Account a : accounts) {
            w.write(formatRecord(a));
            w.write('\n');
            balanceTotal = balanceTotal.add(a.getBalance());
            accruedTotal = accruedTotal.add(a.getAccruedInterest());
        }
        w.write(StringUtils.rightPad("T" + StringUtils.leftPad(String.valueOf(accounts.size()), 9, '0')
                + numeric(balanceTotal, 17, 0) + numeric(accruedTotal, 17, 2), RECORD_LENGTH));
        w.write('\n');
        w.flush();
        return accounts.size();
    }

    static String formatRecord(Account a) {
        StringBuilder sb = new StringBuilder(RECORD_LENGTH);
        sb.append('D');
        sb.append(a.getBranchCode());
        sb.append(a.getAccountNo());
        sb.append(a.getAccountType().getZenginCode());
        sb.append(statusFlag(a.getStatus()));
        sb.append(numeric(a.getBalance(), 15, 0));
        sb.append(numeric(a.getInterestRate(), 7, 4));
        sb.append(numeric(a.getAccruedInterest(), 17, 2));
        return sb.toString();
    }

    /** ホストからの未払利息ファイルを取り込み, 未払利息を上書きする. */
    @Transactional
    public ImportResult importAccrued(InputStream in) throws IOException {
        List<String> lines = IOUtils.readLines(in, HOST_CHARSET);
        ImportResult result = new ImportResult();
        long declaredCount = -1;
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            char kind = line.charAt(0);
            if (kind == 'H') {
                result.processingDate = line.substring(1, 9);
            } else if (kind == 'T') {
                declaredCount = Long.parseLong(line.substring(1, 10));
            } else if (kind == 'D') {
                if (line.length() < RECORD_LENGTH) {
                    throw new BankingException("UB-9001", "レコード長不正: " + line);
                }
                String branch = line.substring(1, 4);
                String accountNo = line.substring(4, 11);
                BigDecimal accrued = new BigDecimal(line.substring(35, 52)).movePointLeft(2);
                Account account = accountRepository.findByBranchCodeAndAccountNo(branch, accountNo);
                if (account == null) {
                    result.skipped++;
                    continue;
                }
                account.setAccruedInterest(accrued);
                accountRepository.save(account);
                result.updated++;
            } else {
                throw new BankingException("UB-9002", "不明なレコード区分: " + kind);
            }
        }
        if (declaredCount >= 0 && declaredCount != result.updated + result.skipped) {
            throw new BankingException("UB-9003", "トレーラ件数不一致: 宣言=" + declaredCount
                    + " 実績=" + (result.updated + result.skipped));
        }
        return result;
    }

    static String numeric(BigDecimal value, int totalDigits, int scale) {
        String digits = value.setScale(scale, RoundingMode.DOWN).movePointRight(scale).toPlainString();
        if (digits.length() > totalDigits) {
            throw new BankingException("UB-9004", "桁あふれ: " + value);
        }
        return StringUtils.leftPad(digits, totalDigits, '0');
    }

    static char statusFlag(AccountStatus status) {
        switch (status) {
        case ACTIVE:
            return 'A';
        case FROZEN:
            return 'F';
        case DORMANT:
            return 'D';
        case CLOSED:
            return 'C';
        default:
            throw new IllegalArgumentException(status.name());
        }
    }

    public static class ImportResult {
        private String processingDate;
        private int updated;
        private int skipped;

        public String getProcessingDate() {
            return processingDate;
        }

        public int getUpdated() {
            return updated;
        }

        public int getSkipped() {
            return skipped;
        }
    }
}
