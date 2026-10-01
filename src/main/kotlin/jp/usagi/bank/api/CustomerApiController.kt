package jp.usagi.bank.api

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import jp.usagi.bank.api.dto.AccountDto
import jp.usagi.bank.api.dto.CustomerDto
import jp.usagi.bank.service.AccountService
import jp.usagi.bank.service.CustomerService

@RestController
@RequestMapping(value = ["/api/customers"], produces = [MediaType.APPLICATION_JSON_UTF8_VALUE])
class CustomerApiController(
    private val customerService: CustomerService,
    private val accountService: AccountService
) {

    @GetMapping
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int
    ): List<CustomerDto> = customerService.search(q, page, size).content.map { CustomerDto.from(it) }

    @GetMapping("/{cifNo}")
    fun get(@PathVariable cifNo: String): CustomerDto = CustomerDto.from(customerService.getByCifNo(cifNo))

    @GetMapping("/{cifNo}/accounts")
    fun accounts(@PathVariable cifNo: String): List<AccountDto> {
        val c = customerService.getByCifNo(cifNo)
        return accountService.listAccountsOf(c.id).map { AccountDto.from(it) }
    }
}
