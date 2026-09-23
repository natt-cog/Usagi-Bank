package jp.usagi.bank.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 画面 (フォーム POST) のロール制御. 画面上で非表示でもサーバ側で拒否されること. */
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class WebSecurityIT {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @Before
    public void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    public void auditorCanViewAccountButCannotDeposit() throws Exception {
        mvc.perform(get("/accounts/001/1000001").with(user("auditor").roles("AUDITOR")))
                .andExpect(status().isOk());
        mvc.perform(post("/accounts/001/1000001/deposit").param("amount", "1")
                .with(user("auditor").roles("AUDITOR")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    public void auditorCannotWithdrawTransferOrRegisterCustomer() throws Exception {
        mvc.perform(post("/accounts/001/1000001/withdraw").param("amount", "1")
                .with(user("auditor").roles("AUDITOR")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/transfer").with(user("auditor").roles("AUDITOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/customers").param("nameKanji", "監査 太郎")
                .with(user("auditor").roles("AUDITOR")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/customers/0000000001/kyc").param("status", "VERIFIED")
                .with(user("auditor").roles("AUDITOR")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    public void tellerCanDepositButCannotChangeStatus() throws Exception {
        mvc.perform(post("/accounts/001/1000001/deposit").param("amount", "1000")
                .with(user("teller").roles("TELLER")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/accounts/001/1000001"));
        mvc.perform(post("/accounts/001/1000001/status").param("status", "FROZEN")
                .with(user("teller").roles("TELLER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    public void adminCanChangeStatus() throws Exception {
        mvc.perform(post("/accounts/001/1000001/status").param("status", "FROZEN")
                .with(user("admin").roles("ADMIN", "TELLER")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    public void postWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/accounts/001/1000001/deposit").param("amount", "1")
                .with(user("teller").roles("TELLER")))
                .andExpect(status().isForbidden());
    }
}
