package jp.usagi.bank.api.dto

import java.math.BigDecimal

import jp.usagi.bank.service.TransferService.TransferResult

data class TransferResponse(
    val referenceNo: String,
    val amount: BigDecimal,
    val fee: BigDecimal,
    val fromBalanceAfter: BigDecimal,
    val toBalanceAfter: BigDecimal
) {

    companion object {
        @JvmStatic
        fun from(r: TransferResult): TransferResponse = TransferResponse(
            referenceNo = r.referenceNo,
            amount = r.debit.amount,
            fee = r.feeAmount,
            fromBalanceAfter = r.fee?.balanceAfter ?: r.debit.balanceAfter,
            toBalanceAfter = r.credit.balanceAfter
        )
    }
}
