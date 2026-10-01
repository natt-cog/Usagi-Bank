package jp.usagi.bank.api

import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

/** REST API の結合テスト (Basic 認証 + ロール制御 + 業務エラー応答). */
@RunWith(SpringRunner::class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BankingApiIT {
    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mvc: MockMvc

    @Before
    fun setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
    }

    @Test
    fun unauthenticatedIsRejected() {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized())
    }

    @Test
    fun tellerCanListAccounts() {
        mvc
            .perform(get("/api/accounts").with(httpBasic("teller", "teller123")))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", Matchers.hasSize<Any>(12))) // 有効口座のみ (FROZEN/DORMANT/CLOSED 除く)
            .andExpect(jsonPath("$[0].branchCode").value("001"))
    }

    @Test
    fun getAccountReturnsJapaneseLabels() {
        mvc
            .perform(get("/api/accounts/001/1000001").with(httpBasic("auditor", "audit123")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customerName").value("山田 太郎"))
            .andExpect(jsonPath("$.accountTypeLabel").value("普通"))
            .andExpect(jsonPath("$.balance").value(1250000))
    }

    @Test
    fun unknownAccountIs404WithErrorCode() {
        mvc
            .perform(get("/api/accounts/001/9999999").with(httpBasic("teller", "teller123")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("UB-1001"))
    }

    @Test
    fun auditorCannotTransfer() {
        mvc
            .perform(
                post("/api/transfers")
                    .with(httpBasic("auditor", "audit123"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"fromBranchCode\":\"001\",\"fromAccountNo\":\"1000001\",\"toBranchCode\":\"002\",\"toAccountNo\":\"2000001\",\"amount\":1000}",
                    ),
            ).andExpect(status().isForbidden())
    }

    @Test
    fun tellerTransferReturnsFeeAndBalances() {
        mvc
            .perform(
                post("/api/transfers")
                    .with(httpBasic("teller", "teller123"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"fromBranchCode\":\"001\",\"fromAccountNo\":\"1000001\",\"toBranchCode\":\"002\",\"toAccountNo\":\"2000001\",\"amount\":10000,\"description\":\"テスト\"}",
                    ),
            ).andExpect(status().isCreated())
            .andExpect(jsonPath("$.fee").value(110))
            .andExpect(jsonPath("$.fromBalanceAfter").value(1239890))
            .andExpect(jsonPath("$.toBalanceAfter").value(390500))
            .andExpect(jsonPath("$.referenceNo", Matchers.startsWith("T")))
    }

    @Test
    fun insufficientFundsIs422() {
        mvc
            .perform(
                post("/api/transfers")
                    .with(httpBasic("teller", "teller123"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"fromBranchCode\":\"004\",\"fromAccountNo\":\"4000001\",\"toBranchCode\":\"001\",\"toAccountNo\":\"1000001\",\"amount\":999999}",
                    ),
            ).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("UB-2001"))
    }

    @Test
    fun validationErrorIs400() {
        mvc
            .perform(
                post("/api/transfers")
                    .with(httpBasic("teller", "teller123"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"fromBranchCode\":\"1\",\"fromAccountNo\":\"1000001\",\"toBranchCode\":\"002\",\"toAccountNo\":\"2000001\",\"amount\":1000}",
                    ),
            ).andExpect(status().isBadRequest())
    }

    @Test
    fun statementXmlIsServed() {
        mvc
            .perform(
                get("/api/accounts/001/1000001/statement")
                    .param("from", "2024-01-01")
                    .param("to", "2024-01-31")
                    .with(httpBasic("teller", "teller123")),
            ).andExpect(status().isOk())
            .andExpect(header().string("Content-Type", Matchers.startsWith("application/xml")))
            .andExpect(content().string(Matchers.containsString("山田 太郎")))
    }

    @Test
    fun batchFileRequiresAdmin() {
        mvc
            .perform(get("/api/batch/accounts-file").with(httpBasic("teller", "teller123")))
            .andExpect(status().isForbidden())
        mvc
            .perform(get("/api/batch/accounts-file").with(httpBasic("admin", "admin123")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", Matchers.containsString("ACCOUNTS.DAT")))
    }
}
