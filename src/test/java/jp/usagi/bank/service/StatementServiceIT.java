package jp.usagi.bank.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jp.usagi.bank.domain.Account;
import jp.usagi.bank.xml.Statement;

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
        Statement st = statementService.buildStatement(a, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

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
        Statement st = statementService.buildStatement(a, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));
        String xml = statementService.toXml(st);

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"), xml);
        assertTrue(xml.contains("xmlns=\"" + Statement.NS + "\"") || xml.contains(":statement xmlns:"), xml);
        assertTrue(xml.contains("<periodFrom>2024-01-01</periodFrom>") || xml.contains(":periodFrom>2024-01-01<"), xml);
        assertTrue(xml.contains("ATM出金 丸の内"), xml);
        assertTrue(xml.contains("ｶ)ｳｻｷﾞｼﾖｳｼﾞ"), xml);
    }
}
