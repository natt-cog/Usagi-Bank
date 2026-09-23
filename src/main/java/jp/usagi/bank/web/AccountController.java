package jp.usagi.bank.web;

import java.math.BigDecimal;
import java.security.Principal;

import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jp.usagi.bank.domain.Account;
import jp.usagi.bank.domain.AccountStatus;
import jp.usagi.bank.domain.AccountType;
import jp.usagi.bank.domain.Transaction;
import jp.usagi.bank.service.AccountService;
import jp.usagi.bank.service.BankingException;
import jp.usagi.bank.service.BusinessDateService;
import jp.usagi.bank.service.StatementService;

@Controller
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private final StatementService statementService;
    private final BusinessDateService businessDateService;

    public AccountController(AccountService accountService, StatementService statementService,
            BusinessDateService businessDateService) {
        this.accountService = accountService;
        this.statementService = statementService;
        this.businessDateService = businessDateService;
    }

    @GetMapping
    public String search(@RequestParam(required = false) String branchCode,
            @RequestParam(required = false) AccountType type,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) BigDecimal minBalance,
            @RequestParam(required = false) BigDecimal maxBalance, Model model) {
        model.addAttribute("accounts", accountService.search(branchCode, type, status, minBalance, maxBalance));
        model.addAttribute("types", AccountType.values());
        model.addAttribute("statuses", AccountStatus.values());
        return "accounts/search";
    }

    @GetMapping("/{branchCode}/{accountNo}")
    public String detail(@PathVariable String branchCode, @PathVariable String accountNo,
            @RequestParam(defaultValue = "0") int page, Model model) {
        Account account = accountService.getAccount(branchCode, accountNo);
        Page<Transaction> txns = accountService.listTransactions(account, page, 20);
        model.addAttribute("account", account);
        model.addAttribute("transactions", txns);
        model.addAttribute("statuses", AccountStatus.values());
        return "accounts/detail";
    }

    @PostMapping("/{branchCode}/{accountNo}/deposit")
    public String deposit(@PathVariable String branchCode, @PathVariable String accountNo,
            @RequestParam BigDecimal amount, @RequestParam(required = false) String description,
            Principal principal, RedirectAttributes ra) {
        try {
            Account account = accountService.getAccount(branchCode, accountNo);
            accountService.deposit(account, amount, description == null ? "窓口入金" : description, principal.getName());
            ra.addFlashAttribute("message", amount.toPlainString() + "円を入金しました");
        } catch (BankingException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/accounts/" + branchCode + "/" + accountNo;
    }

    @PostMapping("/{branchCode}/{accountNo}/withdraw")
    public String withdraw(@PathVariable String branchCode, @PathVariable String accountNo,
            @RequestParam BigDecimal amount, @RequestParam(required = false) String description,
            Principal principal, RedirectAttributes ra) {
        try {
            Account account = accountService.getAccount(branchCode, accountNo);
            accountService.withdraw(account, amount, description == null ? "窓口出金" : description, principal.getName());
            ra.addFlashAttribute("message", amount.toPlainString() + "円を出金しました");
        } catch (BankingException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/accounts/" + branchCode + "/" + accountNo;
    }

    @PostMapping("/{branchCode}/{accountNo}/status")
    public String changeStatus(@PathVariable String branchCode, @PathVariable String accountNo,
            @RequestParam AccountStatus status, RedirectAttributes ra) {
        try {
            accountService.changeStatus(accountService.getAccount(branchCode, accountNo), status);
            ra.addFlashAttribute("message", "口座状態を「" + status.getLabel() + "」に変更しました");
        } catch (BankingException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/accounts/" + branchCode + "/" + accountNo;
    }

    @GetMapping(value = "/{branchCode}/{accountNo}/statement.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String statement(@PathVariable String branchCode, @PathVariable String accountNo,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        Account account = accountService.getAccount(branchCode, accountNo);
        LocalDate toDate = to == null ? businessDateService.today() : LocalDate.parse(to);
        LocalDate fromDate = from == null ? toDate.minusMonths(1) : LocalDate.parse(from);
        return statementService.toXml(statementService.buildStatement(account, fromDate, toDate));
    }
}
