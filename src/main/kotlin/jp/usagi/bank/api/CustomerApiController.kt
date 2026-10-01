package jp.usagi.bank.api

import jp.usagi.bank.api.dto.AccountDto
import jp.usagi.bank.api.dto.CustomerDto
import jp.usagi.bank.domain.Account
import jp.usagi.bank.domain.Customer
import jp.usagi.bank.service.AccountService
import jp.usagi.bank.service.CustomerService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(value = ["/api/customers"], produces = [MediaType.APPLICATION_JSON_UTF8_VALUE])
class CustomerApiController(
    private val customerService: CustomerService,
    private val accountService: AccountService,
) {
    @GetMapping
    fun search(
        @RequestParam("q", required = false) q: String?,
        @RequestParam("page", defaultValue = "0") page: Int,
        @RequestParam("size", defaultValue = "20") size: Int,
    ): List<CustomerDto> {
        val result = ArrayList<CustomerDto>()
        for (c: Customer in customerService.search(q, page, size).content) {
            result.add(CustomerDto.from(c))
        }
        return result
    }

    @GetMapping("/{cifNo}")
    fun get(
        @PathVariable("cifNo") cifNo: String,
    ): CustomerDto = CustomerDto.from(customerService.getByCifNo(cifNo))

    @GetMapping("/{cifNo}/accounts")
    fun accounts(
        @PathVariable("cifNo") cifNo: String,
    ): List<AccountDto> {
        val c: Customer = customerService.getByCifNo(cifNo)
        val result = ArrayList<AccountDto>()
        for (a: Account in accountService.listAccountsOf(c.id)) {
            result.add(AccountDto.from(a))
        }
        return result
    }
}
