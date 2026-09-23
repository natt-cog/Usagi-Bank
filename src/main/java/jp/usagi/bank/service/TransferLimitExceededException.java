package jp.usagi.bank.service;

import java.math.BigDecimal;

public class TransferLimitExceededException extends BankingException {

    private static final long serialVersionUID = 1L;

    public TransferLimitExceededException(BigDecimal limit) {
        super("UB-2002", "1日あたりの振込限度額 (" + limit.toPlainString() + "円) を超過します");
    }
}
