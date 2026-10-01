package jp.usagi.bank.api

import jp.usagi.bank.api.dto.AccountDto
import jp.usagi.bank.api.dto.TransactionDto
import jp.usagi.bank.service.AccountService
import jp.usagi.bank.service.StatementService
import org.joda.time.LocalDate
import org.springframework.data.domain.Page
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(value = ["/api/accounts"], produces = [MediaType.APPLICATION_JSON_UTF8_VALUE])
class AccountApiController(
    private val accountService: AccountService,
    private val statementService: StatementService,
) {

    @GetMapping
    fun list(): List<AccountDto> = accountService.listActiveAccounts().map { AccountDto.from(it) }

    @GetMapping("/{branchCode}/{accountNo}")
    fun get(@PathVariable branchCode: String, @PathVariable accountNo: String): AccountDto =
        AccountDto.from(accountService.getAccount(branchCode, accountNo))

    @GetMapping("/{branchCode}/{accountNo}/transactions")
    fun transactions(
        @PathVariable branchCode: String,
        @PathVariable accountNo: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Page<TransactionDto> {
        val account = accountService.getAccount(branchCode, accountNo)
        return accountService.listTransactions(account, page, size).map { TransactionDto.from(it) }
    }

    @GetMapping(value = ["/{branchCode}/{accountNo}/statement"], produces = [MediaType.APPLICATION_XML_VALUE])
    fun statement(
        @PathVariable branchCode: String,
        @PathVariable accountNo: String,
        @RequestParam from: String,
        @RequestParam to: String,
    ): String {
        val account = accountService.getAccount(branchCode, accountNo)
        return statementService.toXml(statementService.buildStatement(account, LocalDate.parse(from), LocalDate.parse(to)))
    }
}
