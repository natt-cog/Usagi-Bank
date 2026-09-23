package jp.usagi.bank.service;

import java.math.BigDecimal;

import jp.usagi.bank.domain.Account;

public class InsufficientFundsException extends BankingException {

    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(Account account, BigDecimal required) {
        super("UB-2001", "残高不足です: " + account.getDisplayNo()
                + " 残高=" + account.getBalance().toPlainString() + " 必要額=" + required.toPlainString());
    }
}
