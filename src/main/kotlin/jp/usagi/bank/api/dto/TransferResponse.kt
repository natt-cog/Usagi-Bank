package jp.usagi.bank.api.dto

import jp.usagi.bank.domain.Transaction
import jp.usagi.bank.service.TransferService.TransferResult
import java.math.BigDecimal

data class TransferResponse(
    val referenceNo: String?,
    val amount: BigDecimal?,
    val fee: BigDecimal?,
    val fromBalanceAfter: BigDecimal?,
    val toBalanceAfter: BigDecimal?,
) {
    companion object {
        @JvmStatic
        fun from(r: TransferResult): TransferResponse {
            val debit: Transaction? = r.debit
            val feeTransaction: Transaction? = r.fee
            val credit: Transaction? = r.credit

            return TransferResponse(
                referenceNo = r.referenceNo,
                amount = debit?.amount,
                fee = r.feeAmount,
                fromBalanceAfter = if (feeTransaction != null) feeTransaction.balanceAfter else debit?.balanceAfter,
                toBalanceAfter = credit?.balanceAfter,
            )
        }
    }
}
