package jp.usagi.bank.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal

import org.apache.commons.io.IOUtils
import org.joda.time.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.transaction.annotation.Transactional

import jp.usagi.bank.domain.Account
import jp.usagi.bank.domain.AccountStatus
import jp.usagi.bank.domain.AccountType
import jp.usagi.bank.service.BatchFileService.ImportResult

/**
 * ホスト連携ファイル (固定長 52 桁, MS932) のゴールデンファイルテスト.
 * COBOL バッチ (batch/cobol/UBEOD001.cbl) と同一レイアウトであることを保証する.
 */
@RunWith(SpringRunner::class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BatchFileServiceIT {

    @Autowired
    lateinit var batchFileService: BatchFileService
    @Autowired
    lateinit var accountService: AccountService
    @Autowired
    lateinit var businessDateService: BusinessDateService

    @Before
    fun fixBusinessDate() {
        businessDateService.override(LocalDate(2018, 3, 15))
    }

    @After
    fun clear() {
        businessDateService.clearOverride()
    }

    @Test
    fun exportMatchesGoldenFile() {
        val out = ByteArrayOutputStream()
        val count = batchFileService.exportAccounts(out)
        val actual = String(out.toByteArray(), BatchFileService.HOST_CHARSET)
        val expected = golden("ACCOUNTS.DAT")
        if (expected != actual && System.getProperty("golden.update") != null) {
            java.nio.file.Files.write(java.nio.file.Paths.get("src/test/resources/golden/ACCOUNTS.DAT"),
                    out.toByteArray())
            fail("golden file updated; re-run")
        }
        assertEquals(expected, actual)
        assertEquals(15, count)
    }

    @Test
    fun everyRecordIs52Chars() {
        val out = ByteArrayOutputStream()
        batchFileService.exportAccounts(out)
        val lines: List<String> = IOUtils.readLines(ByteArrayInputStream(out.toByteArray()), BatchFileService.HOST_CHARSET)
        for (line in lines) {
            assertEquals(line, BatchFileService.RECORD_LENGTH, line.length)
        }
        assertTrue(lines[0].startsWith("H20180315"))
        assertTrue(lines[lines.size - 1].startsWith("T000000015"))
    }

    @Test
    fun formatRecordLayout() {
        val a = Account()
        a.branchCode = "001"
        a.accountNo = "1000001"
        a.accountType = AccountType.ORDINARY
        a.status = AccountStatus.ACTIVE
        a.balance = BigDecimal("1250000")
        a.interestRate = BigDecimal("0.0010")
        a.accruedInterest = BigDecimal("12.34")
        //            D 店番 口座番号 科目 状態 残高(15)          利率(7) 未払利息(17)
        assertEquals("D" + "001" + "1000001" + "1" + "A" + "000000001250000" + "0000010" + "00000000000001234",
                BatchFileService.formatRecord(a))
    }

    @Test
    fun numericOverflowIsRejected() {
        try {
            BatchFileService.numeric(BigDecimal("1234567890123456"), 15, 0)
            fail()
        } catch (e: BankingException) {
            assertEquals("UB-9004", e.errorCode)
        }
    }

    @Test
    fun importAccruedRoundTrip() {
        val file = golden("ACCRUED_IN.DAT")
        val r: ImportResult = batchFileService.importAccrued(ByteArrayInputStream(file.toByteArray(BatchFileService.HOST_CHARSET)))
        assertEquals("20180315", r.processingDate)
        assertEquals(2, r.updated)
        assertEquals(1, r.skipped)
        assertEquals(BigDecimal("99.99"), accountService.getAccount("001", "1000001").accruedInterest)
        assertEquals(BigDecimal("0.01"), accountService.getAccount("005", "5000002").accruedInterest)
    }

    @Test
    fun trailerCountMismatchIsRejected() {
        val bad = golden("ACCRUED_IN.DAT").replace("T000000003", "T000000002")
        try {
            batchFileService.importAccrued(ByteArrayInputStream(bad.toByteArray(BatchFileService.HOST_CHARSET)))
            fail()
        } catch (e: BankingException) {
            assertEquals("UB-9003", e.errorCode)
        }
    }

    companion object {
        @Throws(IOException::class)
        private fun golden(name: String): String {
            val input: InputStream = requireNotNull(BatchFileServiceIT::class.java.getResourceAsStream("/golden/$name")) {
                "golden resource not found: $name"
            }
            return input.use { IOUtils.toString(it, BatchFileService.HOST_CHARSET) }
        }
    }
}
