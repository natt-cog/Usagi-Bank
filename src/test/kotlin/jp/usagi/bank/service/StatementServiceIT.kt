package jp.usagi.bank.service

import jp.usagi.bank.domain.Account
import jp.usagi.bank.xml.Statement
import org.joda.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@RunWith(SpringRunner::class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StatementServiceIT {

    @Autowired
    lateinit var statementService: StatementService

    @Autowired
    lateinit var accountService: AccountService

    @Test
    fun buildsStatementWithOpeningAndClosingBalance() {
        val a: Account = accountService.getAccount("001", "1000001")
        val st: Statement = statementService.buildStatement(a, LocalDate(2024, 1, 1), LocalDate(2024, 1, 31))

        assertEquals("001", st.branchCode)
        assertEquals("本店営業部", st.branchName)
        assertEquals("山田 太郎", st.customerName)
        assertEquals(2, st.entries.size)
        assertEquals(BigDecimal("1000000"), st.openingBalance)
        assertEquals(BigDecimal("1250000"), st.closingBalance)
    }

    @Test
    fun marshalsToNamespacedXml() {
        val a: Account = accountService.getAccount("001", "1000001")
        val st: Statement = statementService.buildStatement(a, LocalDate(2024, 1, 1), LocalDate(2024, 1, 31))
        val xml = statementService.toXml(st)

        assertTrue(xml, xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"))
        assertTrue(xml, xml.contains("xmlns=\"" + Statement.NS + "\"") || xml.contains(":statement xmlns:"))
        assertTrue(xml, xml.contains("<periodFrom>2024-01-01</periodFrom>") || xml.contains(":periodFrom>2024-01-01<"))
        assertTrue(xml, xml.contains("ATM出金 丸の内"))
        assertTrue(xml, xml.contains("ｶ)ｳｻｷﾞｼﾖｳｼﾞ"))
    }
}
