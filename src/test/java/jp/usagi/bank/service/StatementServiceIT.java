package jp.usagi.bank.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import jp.usagi.bank.domain.Account;
import jp.usagi.bank.xml.Statement;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class StatementServiceIT {

    @Autowired
    private StatementService statementService;
    @Autowired
    private AccountService accountService;

    @Test
    public void buildsStatementWithOpeningAndClosingBalance() {
        Account a = accountService.getAccount("001", "1000001");
        Statement st = statementService.buildStatement(a, new LocalDate(2024, 1, 1), new LocalDate(2024, 1, 31));

        assertEquals("001", st.getBranchCode());
        assertEquals("本店営業部", st.getBranchName());
        assertEquals("山田 太郎", st.getCustomerName());
        assertEquals(2, st.getEntries().size());
        assertEquals(new BigDecimal("1000000"), st.getOpeningBalance());
        assertEquals(new BigDecimal("1250000"), st.getClosingBalance());
    }

    @Test
    public void marshalsToNamespacedXml() {
        Account a = accountService.getAccount("001", "1000001");
        Statement st = statementService.buildStatement(a, new LocalDate(2024, 1, 1), new LocalDate(2024, 1, 31));
        String xml = statementService.toXml(st);

        assertTrue(xml, xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"));
        assertTrue(xml, xml.contains("xmlns=\"" + Statement.NS + "\"") || xml.contains(":statement xmlns:"));
        assertTrue(xml, xml.contains("<periodFrom>2024-01-01</periodFrom>") || xml.contains(":periodFrom>2024-01-01<"));
        assertTrue(xml, xml.contains("ATM出金 丸の内"));
        assertTrue(xml, xml.contains("ｶ)ｳｻｷﾞｼﾖｳｼﾞ"));
    }
}
