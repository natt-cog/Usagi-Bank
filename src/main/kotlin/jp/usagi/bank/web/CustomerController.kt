package jp.usagi.bank.web

import jp.usagi.bank.domain.AccountType
import jp.usagi.bank.domain.Customer
import jp.usagi.bank.domain.KycStatus
import jp.usagi.bank.repository.BranchRepository
import jp.usagi.bank.service.AccountService
import jp.usagi.bank.service.BankingException
import jp.usagi.bank.service.CustomerService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.math.BigDecimal
import java.security.Principal
import javax.validation.Valid

@Controller
@RequestMapping("/customers")
class CustomerController(
    private val customerService: CustomerService,
    private val accountService: AccountService,
    private val branchRepository: BranchRepository,
) {

    @GetMapping
    fun list(@RequestParam(required = false) q: String?, @RequestParam(defaultValue = "0") page: Int, model: Model): String {
        model.addAttribute("q", q)
        model.addAttribute("customers", customerService.search(q, page, 20))
        return "customers/list"
    }

    @GetMapping("/new")
    fun newForm(model: Model): String {
        if (!model.containsAttribute("customer")) {
            model.addAttribute("customer", Customer())
        }
        return "customers/form"
    }

    @PostMapping
    fun create(
        @Valid @ModelAttribute("customer") customer: Customer,
        binding: BindingResult,
        model: Model,
        ra: RedirectAttributes,
    ): String {
        if (binding.hasErrors()) {
            return "customers/form"
        }
        try {
            val saved: Customer = customerService.register(customer)
            ra.addFlashAttribute("message", "顧客を登録しました")
            return "redirect:/customers/" + saved.cifNo
        } catch (e: BankingException) {
            model.addAttribute("error", e.message)
            return "customers/form"
        }
    }

    @GetMapping("/{cifNo}")
    fun detail(@PathVariable cifNo: String, model: Model): String {
        val c: Customer = customerService.getByCifNo(cifNo)
        model.addAttribute("customer", c)
        model.addAttribute("accounts", accountService.listAccountsOf(c.id))
        model.addAttribute("branches", branchRepository.findAll())
        model.addAttribute("types", AccountType.values())
        model.addAttribute("kycStatuses", KycStatus.values())
        return "customers/detail"
    }

    @PostMapping("/{cifNo}/accounts")
    fun openAccount(
        @PathVariable cifNo: String,
        @RequestParam branchCode: String,
        @RequestParam type: AccountType,
        @RequestParam(required = false) initialDeposit: BigDecimal?,
        principal: Principal,
        ra: RedirectAttributes,
    ): String {
        try {
            accountService.openAccount(cifNo, branchCode, type, initialDeposit, principal.name)
            ra.addFlashAttribute("message", "口座を開設しました")
        } catch (e: BankingException) {
            ra.addFlashAttribute("error", e.message)
        }
        return "redirect:/customers/$cifNo"
    }

    @PostMapping("/{cifNo}/kyc")
    fun updateKyc(@PathVariable cifNo: String, @RequestParam status: KycStatus, ra: RedirectAttributes): String {
        val c: Customer = customerService.getByCifNo(cifNo)
        customerService.updateKyc(requireNotNull(c.id) { "persisted customer has an id" }, status)
        ra.addFlashAttribute("message", "本人確認ステータスを更新しました")
        return "redirect:/customers/$cifNo"
    }
}
