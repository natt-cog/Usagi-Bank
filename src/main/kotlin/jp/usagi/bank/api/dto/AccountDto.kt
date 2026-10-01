package jp.usagi.bank.api.dto

import com.fasterxml.jackson.annotation.JsonFormat
import jp.usagi.bank.domain.Account
import java.math.BigDecimal
import java.util.Date

data class AccountDto(
    val id: Long?,
    val branchCode: String?,
    val accountNo: String?,
    val accountType: String?,
    val accountTypeLabel: String?,
    val status: String?,
    val customerCifNo: String?,
    val customerName: String?,
    val balance: BigDecimal,
    val interestRate: BigDecimal,
    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Tokyo")
    val openedOn: Date?,
) {
    companion object {
        @JvmStatic
        fun from(a: Account): AccountDto = AccountDto(
            id = a.id,
            branchCode = a.branchCode,
            accountNo = a.accountNo,
            accountType = a.accountType?.name,
            accountTypeLabel = a.accountType?.label,
            status = a.status.name,
            customerCifNo = a.customer?.cifNo,
            customerName = a.customer?.nameKanji,
            balance = a.balance,
            interestRate = a.interestRate,
            openedOn = a.openedOn,
        )
    }
}
