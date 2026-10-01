package jp.usagi.bank.web

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

/** 画面 (フォーム POST) のロール制御. 画面上で非表示でもサーバ側で拒否されること. */
@RunWith(SpringRunner::class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WebSecurityIT {

    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mvc: MockMvc

    @Before
    fun setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
    }

    @Test
    fun auditorCanViewAccountButCannotDeposit() {
        mvc.perform(get("/accounts/001/1000001").with(user("auditor").roles("AUDITOR")))
                .andExpect(status().isOk)
        mvc.perform(post("/accounts/001/1000001/deposit").param("amount", "1")
                .with(user("auditor").roles("AUDITOR")).with(csrf()))
                .andExpect(status().isForbidden)
    }

    @Test
    fun auditorCannotWithdrawTransferOrRegisterCustomer() {
        mvc.perform(post("/accounts/001/1000001/withdraw").param("amount", "1")
                .with(user("auditor").roles("AUDITOR")).with(csrf()))
                .andExpect(status().isForbidden)
        mvc.perform(get("/transfer").with(user("auditor").roles("AUDITOR")))
                .andExpect(status().isForbidden)
        mvc.perform(post("/customers").param("nameKanji", "監査 太郎")
                .with(user("auditor").roles("AUDITOR")).with(csrf()))
                .andExpect(status().isForbidden)
        mvc.perform(post("/customers/0000000001/kyc").param("status", "VERIFIED")
                .with(user("auditor").roles("AUDITOR")).with(csrf()))
                .andExpect(status().isForbidden)
    }

    @Test
    fun tellerCanDepositButCannotChangeStatus() {
        mvc.perform(post("/accounts/001/1000001/deposit").param("amount", "1000")
                .with(user("teller").roles("TELLER")).with(csrf()))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/accounts/001/1000001"))
        mvc.perform(post("/accounts/001/1000001/status").param("status", "FROZEN")
                .with(user("teller").roles("TELLER")).with(csrf()))
                .andExpect(status().isForbidden)
    }

    @Test
    fun adminCanChangeStatus() {
        mvc.perform(post("/accounts/001/1000001/status").param("status", "FROZEN")
                .with(user("admin").roles("ADMIN", "TELLER")).with(csrf()))
                .andExpect(status().is3xxRedirection)
    }

    @Test
    fun postWithoutCsrfIsRejected() {
        mvc.perform(post("/accounts/001/1000001/deposit").param("amount", "1")
                .with(user("teller").roles("TELLER")))
                .andExpect(status().isForbidden)
    }
}
