package jp.usagi.bank.service

import jp.usagi.bank.domain.Account
import jp.usagi.bank.domain.AccountStatus
import jp.usagi.bank.domain.AccountType
import org.apache.commons.io.IOUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

/**
 * COBOL バッチ (batch/cobol/UBEOD001.cbl) と Java 利息計算の同値性テスト.
 *
 * golden/ACCOUNTS.DAT を UBEOD001 に入力した結果 (golden/ACCRUED_COBOL.DAT) と,
 * 同じ入力に [InterestService.dailyInterest] を適用した結果が
 * 1 銭単位で一致することを検証する.
 */
class CobolParityTest {
    @Test
    @Throws(IOException::class)
    fun javaInterestMatchesCobolOutput() {
        val input = golden("ACCOUNTS.DAT")
        val cobol = golden("ACCRUED_COBOL.DAT")
        assertEquals(input.size.toLong(), cobol.size.toLong())

        var compared = 0
        for (i in input.indices) {
            val src = input[i]
            if (src[0] != 'D') {
                continue
            }
            val a = parse(src)
            var expectedAccrued = a.accruedInterest
            if (InterestService.bearsInterest(a)) {
                expectedAccrued = expectedAccrued.add(InterestService.dailyInterest(a.balance, a.interestRate))
            }
            a.accruedInterest = expectedAccrued
            assertEquals("record " + (i + 1), cobol[i], BatchFileService.formatRecord(a))
            compared++
        }
        assertEquals(15, compared.toLong())
    }

    companion object {
        private fun parse(line: String): Account {
            val a = Account()
            a.branchCode = line.substring(1, 4)
            a.accountNo = line.substring(4, 11)
            a.accountType = typeOf(line[11])
            a.status = statusOf(line[12])
            a.balance = BigDecimal(line.substring(13, 28))
            a.interestRate = BigDecimal(line.substring(28, 35)).movePointLeft(4)
            a.accruedInterest = BigDecimal(line.substring(35, 52)).movePointLeft(2)
            return a
        }

        private fun typeOf(zengin: Char): AccountType {
            for (t in AccountType.values()) {
                if (t.zenginCode[0] == zengin) {
                    return t
                }
            }
            throw IllegalArgumentException("type $zengin")
        }

        private fun statusOf(flag: Char): AccountStatus =
            when (flag) {
                'A' -> AccountStatus.ACTIVE
                'F' -> AccountStatus.FROZEN
                'D' -> AccountStatus.DORMANT
                'C' -> AccountStatus.CLOSED
                else -> throw IllegalArgumentException("status $flag")
            }

        @Throws(IOException::class)
        private fun golden(name: String): List<String> {
            val input = checkNotNull(CobolParityTest::class.java.getResourceAsStream("/golden/$name")) { "/golden/$name" }
            return input.use { IOUtils.readLines(it, BatchFileService.HOST_CHARSET) }
        }
    }
}
