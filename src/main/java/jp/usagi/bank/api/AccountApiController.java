package jp.usagi.bank.api;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jp.usagi.bank.api.dto.AccountDto;
import jp.usagi.bank.api.dto.TransactionDto;
import jp.usagi.bank.domain.Account;
import jp.usagi.bank.domain.Transaction;
import jp.usagi.bank.service.AccountService;
import jp.usagi.bank.service.StatementService;

@RestController
@RequestMapping(value = "/api/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
public class AccountApiController {

    private final AccountService accountService;
    private final StatementService statementService;

    public AccountApiController(AccountService accountService, StatementService statementService) {
        this.accountService = accountService;
        this.statementService = statementService;
    }

    @GetMapping
    public List<AccountDto> list() {
        List<AccountDto> result = new ArrayList<AccountDto>();
        for (Account a : accountService.listActiveAccounts()) {
            result.add(AccountDto.from(a));
        }
        return result;
    }

    @GetMapping("/{branchCode}/{accountNo}")
    public AccountDto get(@PathVariable String branchCode, @PathVariable String accountNo) {
        return AccountDto.from(accountService.getAccount(branchCode, accountNo));
    }

    @GetMapping("/{branchCode}/{accountNo}/transactions")
    public Page<TransactionDto> transactions(@PathVariable String branchCode, @PathVariable String accountNo,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Account account = accountService.getAccount(branchCode, accountNo);
        Page<Transaction> txns = accountService.listTransactions(account, page, size);
        return txns.map(TransactionDto::from);
    }

    @GetMapping(value = "/{branchCode}/{accountNo}/statement", produces = MediaType.APPLICATION_XML_VALUE)
    public String statement(@PathVariable String branchCode, @PathVariable String accountNo,
            @RequestParam String from, @RequestParam String to) {
        Account account = accountService.getAccount(branchCode, accountNo);
        return statementService.toXml(statementService.buildStatement(account, LocalDate.parse(from), LocalDate.parse(to)));
    }
}
