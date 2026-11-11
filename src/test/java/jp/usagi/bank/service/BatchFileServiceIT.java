package jp.usagi.bank.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import org.apache.commons.io.IOUtils;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jp.usagi.bank.domain.Account;
import jp.usagi.bank.domain.AccountStatus;
import jp.usagi.bank.domain.AccountType;
import jp.usagi.bank.service.BatchFileService.ImportResult;

/**
 * ホスト連携ファイル (固定長 52 桁, MS932) のゴールデンファイルテスト.
 * COBOL バッチ (batch/cobol/UBEOD001.cbl) と同一レイアウトであることを保証する.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class BatchFileServiceIT {

    @Autowired
    private BatchFileService batchFileService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private BusinessDateService businessDateService;

    @BeforeEach
    public void fixBusinessDate() {
        businessDateService.override(LocalDate.of(2018, 3, 15));
    }

    @AfterEach
    public void clear() {
        businessDateService.clearOverride();
    }

    @Test
    public void exportMatchesGoldenFile() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int count = batchFileService.exportAccounts(out);
        String actual = new String(out.toByteArray(), BatchFileService.HOST_CHARSET);
        String expected = golden("ACCOUNTS.DAT");
        if (!expected.equals(actual) && System.getProperty("golden.update") != null) {
            java.nio.file.Files.write(java.nio.file.Paths.get("src/test/resources/golden/ACCOUNTS.DAT"),
                    out.toByteArray());
            fail("golden file updated; re-run");
        }
        assertEquals(expected, actual);
        assertEquals(15, count);
    }

    @Test
    public void everyRecordIs52Chars() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        batchFileService.exportAccounts(out);
        List<String> lines = IOUtils.readLines(new ByteArrayInputStream(out.toByteArray()), BatchFileService.HOST_CHARSET);
        for (String line : lines) {
            assertEquals(BatchFileService.RECORD_LENGTH, line.length(), line);
        }
        assertTrue(lines.get(0).startsWith("H20180315"));
        assertTrue(lines.get(lines.size() - 1).startsWith("T000000015"));
    }

    @Test
    public void formatRecordLayout() {
        Account a = new Account();
        a.setBranchCode("001");
        a.setAccountNo("1000001");
        a.setAccountType(AccountType.ORDINARY);
        a.setStatus(AccountStatus.ACTIVE);
        a.setBalance(new BigDecimal("1250000"));
        a.setInterestRate(new BigDecimal("0.0010"));
        a.setAccruedInterest(new BigDecimal("12.34"));
        //            D 店番 口座番号 科目 状態 残高(15)          利率(7) 未払利息(17)
        assertEquals("D" + "001" + "1000001" + "1" + "A" + "000000001250000" + "0000010" + "00000000000001234",
                BatchFileService.formatRecord(a));
    }

    @Test
    public void numericOverflowIsRejected() {
        try {
            BatchFileService.numeric(new BigDecimal("1234567890123456"), 15, 0);
            fail();
        } catch (BankingException e) {
            assertEquals("UB-9004", e.getErrorCode());
        }
    }

    @Test
    public void importAccruedRoundTrip() throws IOException {
        String file = golden("ACCRUED_IN.DAT");
        ImportResult r = batchFileService.importAccrued(new ByteArrayInputStream(file.getBytes(BatchFileService.HOST_CHARSET)));
        assertEquals("20180315", r.getProcessingDate());
        assertEquals(2, r.getUpdated());
        assertEquals(1, r.getSkipped());
        assertEquals(new BigDecimal("99.99"), accountService.getAccount("001", "1000001").getAccruedInterest());
        assertEquals(new BigDecimal("0.01"), accountService.getAccount("005", "5000002").getAccruedInterest());
    }

    @Test
    public void trailerCountMismatchIsRejected() throws IOException {
        String bad = golden("ACCRUED_IN.DAT").replace("T000000003", "T000000002");
        try {
            batchFileService.importAccrued(new ByteArrayInputStream(bad.getBytes(BatchFileService.HOST_CHARSET)));
            fail();
        } catch (BankingException e) {
            assertEquals("UB-9003", e.getErrorCode());
        }
    }

    private static String golden(String name) throws IOException {
        InputStream in = BatchFileServiceIT.class.getResourceAsStream("/golden/" + name);
        try {
            return IOUtils.toString(in, BatchFileService.HOST_CHARSET);
        } finally {
            in.close();
        }
    }
}
