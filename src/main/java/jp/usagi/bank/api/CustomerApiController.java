package jp.usagi.bank.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jp.usagi.bank.api.dto.AccountDto;
import jp.usagi.bank.api.dto.CustomerDto;
import jp.usagi.bank.domain.Account;
import jp.usagi.bank.domain.Customer;
import jp.usagi.bank.service.AccountService;
import jp.usagi.bank.service.CustomerService;

@RestController
@RequestMapping(value = "/api/customers", produces = MediaType.APPLICATION_JSON_VALUE)
public class CustomerApiController {

    private final CustomerService customerService;
    private final AccountService accountService;

    public CustomerApiController(CustomerService customerService, AccountService accountService) {
        this.customerService = customerService;
        this.accountService = accountService;
    }

    @GetMapping
    public List<CustomerDto> search(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        List<CustomerDto> result = new ArrayList<CustomerDto>();
        for (Customer c : customerService.search(q, page, size)) {
            result.add(CustomerDto.from(c));
        }
        return result;
    }

    @GetMapping("/{cifNo}")
    public CustomerDto get(@PathVariable String cifNo) {
        return CustomerDto.from(customerService.getByCifNo(cifNo));
    }

    @GetMapping("/{cifNo}/accounts")
    public List<AccountDto> accounts(@PathVariable String cifNo) {
        Customer c = customerService.getByCifNo(cifNo);
        List<AccountDto> result = new ArrayList<AccountDto>();
        for (Account a : accountService.listAccountsOf(c.getId())) {
            result.add(AccountDto.from(a));
        }
        return result;
    }
}
