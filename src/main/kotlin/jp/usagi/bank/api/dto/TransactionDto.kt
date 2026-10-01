package jp.usagi.bank.api.dto

import com.fasterxml.jackson.annotation.JsonFormat
import jp.usagi.bank.domain.Transaction
import java.math.BigDecimal
import java.util.Date

data class TransactionDto(
    val id: Long?,
    val type: String?,
    val typeLabel: String?,
    val isCredit: Boolean,
    val amount: BigDecimal?,
    val balanceAfter: BigDecimal?,
    val description: String?,
    val referenceNo: String?,
    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Tokyo")
    val valueDate: Date?,
    @field:JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd'T'HH:mm:ss",
        timezone = "Asia/Tokyo",
    )
    val postedAt: Date?,
) {
    companion object {
        @JvmStatic
        fun from(t: Transaction): TransactionDto = TransactionDto(
            id = t.id,
            type = t.type?.name,
            typeLabel = t.type?.label,
            isCredit = t.type?.isCredit ?: false,
            amount = t.amount,
            balanceAfter = t.balanceAfter,
            description = t.description,
            referenceNo = t.referenceNo,
            valueDate = t.valueDate,
            postedAt = t.postedAt,
        )
    }
}
