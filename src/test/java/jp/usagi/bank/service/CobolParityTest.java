package jp.usagi.bank.service;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import jp.usagi.bank.domain.Account;
import jp.usagi.bank.domain.AccountStatus;
import jp.usagi.bank.domain.AccountType;

/**
 * COBOL バッチ (batch/cobol/UBEOD001.cbl) と Java 利息計算の同値性テスト.
 *
 * golden/ACCOUNTS.DAT を UBEOD001 に入力した結果 (golden/ACCRUED_COBOL.DAT) と,
 * 同じ入力に {@link InterestService#dailyInterest} を適用した結果が
 * 1 銭単位で一致することを検証する.
 */
public class CobolParityTest {

    @Test
    public void javaInterestMatchesCobolOutput() throws IOException {
        List<String> in = golden("ACCOUNTS.DAT");
        List<String> cobol = golden("ACCRUED_COBOL.DAT");
        assertEquals(in.size(), cobol.size());

        int compared = 0;
        for (int i = 0; i < in.size(); i++) {
            String src = in.get(i);
            if (src.charAt(0) != 'D') {
                continue;
            }
            Account a = parse(src);
            BigDecimal expectedAccrued = a.getAccruedInterest();
            if (InterestService.bearsInterest(a)) {
                expectedAccrued = expectedAccrued.add(InterestService.dailyInterest(a.getBalance(), a.getInterestRate()));
            }
            a.setAccruedInterest(expectedAccrued);
            assertEquals("record " + (i + 1), cobol.get(i), BatchFileService.formatRecord(a));
            compared++;
        }
        assertEquals(15, compared);
    }

    private static Account parse(String line) {
        Account a = new Account();
        a.setBranchCode(line.substring(1, 4));
        a.setAccountNo(line.substring(4, 11));
        a.setAccountType(typeOf(line.charAt(11)));
        a.setStatus(statusOf(line.charAt(12)));
        a.setBalance(new BigDecimal(line.substring(13, 28)));
        a.setInterestRate(new BigDecimal(line.substring(28, 35)).movePointLeft(4));
        a.setAccruedInterest(new BigDecimal(line.substring(35, 52)).movePointLeft(2));
        return a;
    }

    private static AccountType typeOf(char zengin) {
        for (AccountType t : AccountType.values()) {
            if (t.getZenginCode().charAt(0) == zengin) {
                return t;
            }
        }
        throw new IllegalArgumentException("type " + zengin);
    }

    private static AccountStatus statusOf(char flag) {
        switch (flag) {
        case 'A': return AccountStatus.ACTIVE;
        case 'F': return AccountStatus.FROZEN;
        case 'D': return AccountStatus.DORMANT;
        case 'C': return AccountStatus.CLOSED;
        default: throw new IllegalArgumentException("status " + flag);
        }
    }

    private static List<String> golden(String name) throws IOException {
        InputStream in = CobolParityTest.class.getResourceAsStream("/golden/" + name);
        try {
            return IOUtils.readLines(in, BatchFileService.HOST_CHARSET);
        } finally {
            in.close();
        }
    }
}
