package jp.usagi.bank.service;

public class AccountNotFoundException extends BankingException {

    private static final long serialVersionUID = 1L;

    public AccountNotFoundException(String branchCode, String accountNo) {
        super("UB-1001", "口座が存在しません: " + branchCode + "-" + accountNo);
    }
}
