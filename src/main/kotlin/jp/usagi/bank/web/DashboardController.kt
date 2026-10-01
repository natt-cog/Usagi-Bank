package jp.usagi.bank.web

import jp.usagi.bank.domain.Account
import jp.usagi.bank.domain.Branch
import jp.usagi.bank.repository.BranchRepository
import jp.usagi.bank.service.AccountService
import jp.usagi.bank.service.BusinessDateService
import jp.usagi.bank.service.CustomerService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import java.math.BigDecimal

@Controller
class DashboardController(
    private val accountService: AccountService,
    private val customerService: CustomerService,
    private val branchRepository: BranchRepository,
    private val businessDateService: BusinessDateService,
) {
    @GetMapping("/dashboard")
    fun dashboard(model: Model): String {
        val accounts: List<Account> = accountService.listActiveAccounts()
        val branchTotals: MutableMap<Branch, BigDecimal> = LinkedHashMap()
        for (b: Branch in branchRepository.findAll()) {
            branchTotals[b] = accountService.totalBalanceOfBranch(b.code)
        }
        model.addAttribute("businessDate", businessDateService.todayAsDate())
        model.addAttribute("accounts", accounts)
        model.addAttribute("accountCount", accounts.size)
        model.addAttribute("branchTotals", branchTotals)
        model.addAttribute("topCustomers", customerService.topCustomers(5))
        return "dashboard"
    }
}
