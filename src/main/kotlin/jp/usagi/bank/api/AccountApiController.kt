package jp.usagi.bank.api

import org.joda.time.LocalDate
import org.springframework.core.convert.converter.Converter
import org.springframework.data.domain.Page
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import jp.usagi.bank.api.dto.AccountDto
import jp.usagi.bank.api.dto.TransactionDto
import jp.usagi.bank.domain.Account
import jp.usagi.bank.domain.Transaction
import jp.usagi.bank.service.AccountService
import jp.usagi.bank.service.StatementService

@RestController
@RequestMapping(value = ["/api/accounts"], produces = [MediaType.APPLICATION_JSON_UTF8_VALUE])
class AccountApiController(
    private val accountService: AccountService,
    private val statementService: StatementService,
) {

    @GetMapping
    fun list(): List<AccountDto> {
        val result = ArrayList<AccountDto>()
        for (a: Account in accountService.listActiveAccounts()) {
            result.add(AccountDto.from(a))
        }
        return result
    }

    @GetMapping("/{branchCode}/{accountNo}")
    fun get(
        @PathVariable("branchCode") branchCode: String,
        @PathVariable("accountNo") accountNo: String,
    ): AccountDto {
        return AccountDto.from(accountService.getAccount(branchCode, accountNo))
    }

    @GetMapping("/{branchCode}/{accountNo}/transactions")
    fun transactions(
        @PathVariable("branchCode") branchCode: String,
        @PathVariable("accountNo") accountNo: String,
        @RequestParam("page", defaultValue = "0") page: Int,
        @RequestParam("size", defaultValue = "20") size: Int,
    ): Page<TransactionDto> {
        val account: Account = accountService.getAccount(branchCode, accountNo)
        val txns: Page<Transaction> = accountService.listTransactions(account, page, size)
        return txns.map(Converter<Transaction, TransactionDto> { TransactionDto.from(it) })
    }

    @GetMapping(value = ["/{branchCode}/{accountNo}/statement"], produces = [MediaType.APPLICATION_XML_VALUE])
    fun statement(
        @PathVariable("branchCode") branchCode: String,
        @PathVariable("accountNo") accountNo: String,
        @RequestParam("from") from: String,
        @RequestParam("to") to: String,
    ): String {
        val account: Account = accountService.getAccount(branchCode, accountNo)
        return statementService.toXml(statementService.buildStatement(account, LocalDate.parse(from), LocalDate.parse(to)))
    }
}
