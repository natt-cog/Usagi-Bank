package jp.usagi.bank.web

import jp.usagi.bank.domain.Account
import jp.usagi.bank.domain.AccountStatus
import jp.usagi.bank.domain.AccountType
import jp.usagi.bank.domain.Transaction
import jp.usagi.bank.service.AccountService
import jp.usagi.bank.service.BankingException
import jp.usagi.bank.service.BusinessDateService
import jp.usagi.bank.service.StatementService
import org.joda.time.LocalDate
import org.springframework.data.domain.Page
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.math.BigDecimal
import java.security.Principal

@Controller
@RequestMapping("/accounts")
class AccountController(
    private val accountService: AccountService,
    private val statementService: StatementService,
    private val businessDateService: BusinessDateService,
) {
    @GetMapping
    fun search(
        @RequestParam("branchCode", required = false) branchCode: String?,
        @RequestParam("type", required = false) type: AccountType?,
        @RequestParam("status", required = false) status: AccountStatus?,
        @RequestParam("minBalance", required = false) minBalance: BigDecimal?,
        @RequestParam("maxBalance", required = false) maxBalance: BigDecimal?,
        model: Model,
    ): String {
        model.addAttribute("accounts", accountService.search(branchCode, type, status, minBalance, maxBalance))
        model.addAttribute("types", AccountType.values())
        model.addAttribute("statuses", AccountStatus.values())
        return "accounts/search"
    }

    @GetMapping("/{branchCode}/{accountNo}")
    fun detail(
        @PathVariable("branchCode") branchCode: String,
        @PathVariable("accountNo") accountNo: String,
        @RequestParam("page", defaultValue = "0") page: Int,
        model: Model,
    ): String {
        val account: Account = accountService.getAccount(branchCode, accountNo)
        val txns: Page<Transaction> = accountService.listTransactions(account, page, 20)
        model.addAttribute("account", account)
        model.addAttribute("transactions", txns)
        model.addAttribute("statuses", AccountStatus.values())
        return "accounts/detail"
    }

    @PostMapping("/{branchCode}/{accountNo}/deposit")
    fun deposit(
        @PathVariable("branchCode") branchCode: String,
        @PathVariable("accountNo") accountNo: String,
        @RequestParam("amount") amount: BigDecimal?,
        @RequestParam("description", required = false) description: String?,
        principal: Principal,
        ra: RedirectAttributes,
    ): String {
        try {
            val account: Account = accountService.getAccount(branchCode, accountNo)
            accountService.deposit(account, amount, description ?: "窓口入金", principal.name)
            ra.addFlashAttribute("message", checkNotNull(amount).toPlainString() + "円を入金しました")
        } catch (e: BankingException) {
            ra.addFlashAttribute("error", e.message)
        }
        return "redirect:/accounts/$branchCode/$accountNo"
    }

    @PostMapping("/{branchCode}/{accountNo}/withdraw")
    fun withdraw(
        @PathVariable("branchCode") branchCode: String,
        @PathVariable("accountNo") accountNo: String,
        @RequestParam("amount") amount: BigDecimal?,
        @RequestParam("description", required = false) description: String?,
        principal: Principal,
        ra: RedirectAttributes,
    ): String {
        try {
            val account: Account = accountService.getAccount(branchCode, accountNo)
            accountService.withdraw(account, amount, description ?: "窓口出金", principal.name)
            ra.addFlashAttribute("message", checkNotNull(amount).toPlainString() + "円を出金しました")
        } catch (e: BankingException) {
            ra.addFlashAttribute("error", e.message)
        }
        return "redirect:/accounts/$branchCode/$accountNo"
    }

    @PostMapping("/{branchCode}/{accountNo}/status")
    fun changeStatus(
        @PathVariable("branchCode") branchCode: String,
        @PathVariable("accountNo") accountNo: String,
        @RequestParam("status") status: AccountStatus?,
        ra: RedirectAttributes,
    ): String {
        try {
            val account: Account = accountService.getAccount(branchCode, accountNo)
            val newStatus: AccountStatus = status ?: throw NullPointerException("status")
            accountService.changeStatus(account, newStatus)
            ra.addFlashAttribute("message", "口座状態を「" + newStatus.label + "」に変更しました")
        } catch (e: BankingException) {
            ra.addFlashAttribute("error", e.message)
        }
        return "redirect:/accounts/$branchCode/$accountNo"
    }

    @GetMapping(value = ["/{branchCode}/{accountNo}/statement.xml"], produces = [MediaType.APPLICATION_XML_VALUE])
    @ResponseBody
    fun statement(
        @PathVariable("branchCode") branchCode: String,
        @PathVariable("accountNo") accountNo: String,
        @RequestParam("from", required = false) from: String?,
        @RequestParam("to", required = false) to: String?,
    ): String {
        val account: Account = accountService.getAccount(branchCode, accountNo)
        val toDate: LocalDate = if (to == null) businessDateService.today() else LocalDate.parse(to)
        val fromDate: LocalDate = if (from == null) toDate.minusMonths(1) else LocalDate.parse(from)
        return statementService.toXml(statementService.buildStatement(account, fromDate, toDate))
    }
}
