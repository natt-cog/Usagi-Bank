package jp.usagi.bank.web;

import java.math.BigDecimal;
import java.security.Principal;

import javax.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jp.usagi.bank.domain.AccountType;
import jp.usagi.bank.domain.Customer;
import jp.usagi.bank.domain.KycStatus;
import jp.usagi.bank.repository.BranchRepository;
import jp.usagi.bank.service.AccountService;
import jp.usagi.bank.service.BankingException;
import jp.usagi.bank.service.CustomerService;

@Controller
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final AccountService accountService;
    private final BranchRepository branchRepository;

    public CustomerController(CustomerService customerService, AccountService accountService,
            BranchRepository branchRepository) {
        this.customerService = customerService;
        this.accountService = accountService;
        this.branchRepository = branchRepository;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("q", q);
        model.addAttribute("customers", customerService.search(q, page, 20));
        return "customers/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("customer")) {
            model.addAttribute("customer", new Customer());
        }
        return "customers/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("customer") Customer customer, BindingResult binding,
            Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            return "customers/form";
        }
        try {
            Customer saved = customerService.register(customer);
            ra.addFlashAttribute("message", "顧客を登録しました");
            return "redirect:/customers/" + saved.getCifNo();
        } catch (BankingException e) {
            model.addAttribute("error", e.getMessage());
            return "customers/form";
        }
    }

    @GetMapping("/{cifNo}")
    public String detail(@PathVariable String cifNo, Model model) {
        Customer c = customerService.getByCifNo(cifNo);
        model.addAttribute("customer", c);
        model.addAttribute("accounts", accountService.listAccountsOf(c.getId()));
        model.addAttribute("branches", branchRepository.findAll());
        model.addAttribute("types", AccountType.values());
        model.addAttribute("kycStatuses", KycStatus.values());
        return "customers/detail";
    }

    @PostMapping("/{cifNo}/accounts")
    public String openAccount(@PathVariable String cifNo, @RequestParam String branchCode,
            @RequestParam AccountType type, @RequestParam(required = false) BigDecimal initialDeposit,
            Principal principal, RedirectAttributes ra) {
        try {
            accountService.openAccount(cifNo, branchCode, type, initialDeposit, principal.getName());
            ra.addFlashAttribute("message", "口座を開設しました");
        } catch (BankingException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/customers/" + cifNo;
    }

    @PostMapping("/{cifNo}/kyc")
    public String updateKyc(@PathVariable String cifNo, @RequestParam KycStatus status, RedirectAttributes ra) {
        Customer c = customerService.getByCifNo(cifNo);
        customerService.updateKyc(c.getId(), status);
        ra.addFlashAttribute("message", "本人確認ステータスを更新しました");
        return "redirect:/customers/" + cifNo;
    }
}
