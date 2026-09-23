package jp.usagi.bank.web;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jp.usagi.bank.domain.Account;
import jp.usagi.bank.domain.Branch;
import jp.usagi.bank.repository.BranchRepository;
import jp.usagi.bank.service.AccountService;
import jp.usagi.bank.service.BusinessDateService;
import jp.usagi.bank.service.CustomerService;

@Controller
public class DashboardController {

    private final AccountService accountService;
    private final CustomerService customerService;
    private final BranchRepository branchRepository;
    private final BusinessDateService businessDateService;

    public DashboardController(AccountService accountService, CustomerService customerService,
            BranchRepository branchRepository, BusinessDateService businessDateService) {
        this.accountService = accountService;
        this.customerService = customerService;
        this.branchRepository = branchRepository;
        this.businessDateService = businessDateService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Account> accounts = accountService.listActiveAccounts();
        Map<Branch, BigDecimal> branchTotals = new LinkedHashMap<Branch, BigDecimal>();
        for (Branch b : branchRepository.findAll()) {
            branchTotals.put(b, accountService.totalBalanceOfBranch(b.getCode()));
        }
        model.addAttribute("businessDate", businessDateService.todayAsDate());
        model.addAttribute("accounts", accounts);
        model.addAttribute("accountCount", accounts.size());
        model.addAttribute("branchTotals", branchTotals);
        model.addAttribute("topCustomers", customerService.topCustomers(5));
        return "dashboard";
    }
}
