package jp.usagi.bank.service;

import jp.usagi.bank.domain.Account;

public class AccountNotActiveException extends BankingException {

    private static final long serialVersionUID = 1L;

    public AccountNotActiveException(Account account) {
        super("UB-1002", "口座が取引可能な状態ではありません: " + account.getDisplayNo()
                + " (" + account.getStatus().getLabel() + ")");
    }
}
