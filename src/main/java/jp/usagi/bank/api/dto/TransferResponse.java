package jp.usagi.bank.api.dto;

import java.math.BigDecimal;

import jp.usagi.bank.service.TransferService.TransferResult;

public class TransferResponse {

    private String referenceNo;
    private BigDecimal amount;
    private BigDecimal fee;
    private BigDecimal fromBalanceAfter;
    private BigDecimal toBalanceAfter;

    public static TransferResponse from(TransferResult r) {
        TransferResponse res = new TransferResponse();
        res.referenceNo = r.getReferenceNo();
        res.amount = r.getDebit().getAmount();
        res.fee = r.getFeeAmount();
        res.fromBalanceAfter = r.getFee() != null ? r.getFee().getBalanceAfter() : r.getDebit().getBalanceAfter();
        res.toBalanceAfter = r.getCredit().getBalanceAfter();
        return res;
    }

    public String getReferenceNo() { return referenceNo; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getFee() { return fee; }
    public BigDecimal getFromBalanceAfter() { return fromBalanceAfter; }
    public BigDecimal getToBalanceAfter() { return toBalanceAfter; }
}
