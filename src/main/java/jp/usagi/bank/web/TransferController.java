package jp.usagi.bank.web;

import java.math.BigDecimal;

import java.security.Principal;

import javax.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jp.usagi.bank.api.dto.TransferRequest;
import jp.usagi.bank.service.BankingException;
import jp.usagi.bank.service.TransferService;
import jp.usagi.bank.service.TransferService.TransferResult;

@Controller
@RequestMapping("/transfer")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @ModelAttribute("dailyLimit")
    public BigDecimal dailyLimit() {
        return transferService.getDailyLimit();
    }

    @GetMapping
    public String form(Model model) {
        if (!model.containsAttribute("transfer")) {
            model.addAttribute("transfer", new TransferRequest());
        }
        return "transfer/form";
    }

    @PostMapping
    public String submit(@Valid @ModelAttribute("transfer") TransferRequest req, BindingResult binding,
            Principal principal, Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            return "transfer/form";
        }
        try {
            TransferResult result = transferService.transfer(req.getFromBranchCode(), req.getFromAccountNo(),
                    req.getToBranchCode(), req.getToAccountNo(), req.getAmount(), req.getDescription(),
                    principal.getName());
            ra.addFlashAttribute("result", result);
            ra.addFlashAttribute("request", req);
            return "redirect:/transfer/complete";
        } catch (BankingException e) {
            model.addAttribute("error", e.getMessage());
            return "transfer/form";
        }
    }

    @GetMapping("/complete")
    public String complete(Model model) {
        if (!model.containsAttribute("result")) {
            return "redirect:/transfer";
        }
        return "transfer/complete";
    }
}
