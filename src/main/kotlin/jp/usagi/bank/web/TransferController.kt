package jp.usagi.bank.web

import jp.usagi.bank.api.dto.TransferRequest
import jp.usagi.bank.service.BankingException
import jp.usagi.bank.service.TransferService
import jp.usagi.bank.service.TransferService.TransferResult
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.math.BigDecimal
import java.security.Principal
import javax.validation.Valid

@Controller
@RequestMapping("/transfer")
class TransferController(private val transferService: TransferService) {

    @ModelAttribute("dailyLimit")
    fun dailyLimit(): BigDecimal = transferService.dailyLimit

    @GetMapping
    fun form(model: Model): String {
        if (!model.containsAttribute("transfer")) {
            model.addAttribute("transfer", TransferRequest())
        }
        return "transfer/form"
    }

    @PostMapping
    fun submit(
        @Valid @ModelAttribute("transfer") req: TransferRequest,
        binding: BindingResult,
        principal: Principal,
        model: Model,
        ra: RedirectAttributes,
    ): String {
        if (binding.hasErrors()) {
            return "transfer/form"
        }
        try {
            val result: TransferResult = transferService.transfer(
                requireNotNull(req.fromBranchCode) { "fromBranchCode is @NotNull" },
                requireNotNull(req.fromAccountNo) { "fromAccountNo is @NotNull" },
                requireNotNull(req.toBranchCode) { "toBranchCode is @NotNull" },
                requireNotNull(req.toAccountNo) { "toAccountNo is @NotNull" },
                req.amount,
                req.description,
                principal.name,
            )
            ra.addFlashAttribute("result", result)
            ra.addFlashAttribute("request", req)
            return "redirect:/transfer/complete"
        } catch (e: BankingException) {
            model.addAttribute("error", e.message)
            return "transfer/form"
        }
    }

    @GetMapping("/complete")
    fun complete(model: Model): String {
        if (!model.containsAttribute("result")) {
            return "redirect:/transfer"
        }
        return "transfer/complete"
    }
}
