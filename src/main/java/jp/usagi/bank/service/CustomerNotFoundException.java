package jp.usagi.bank.service;

public class CustomerNotFoundException extends BankingException {

    private static final long serialVersionUID = 1L;

    public CustomerNotFoundException(String cifNo) {
        super("UB-1003", "顧客が存在しません: CIF=" + cifNo);
    }
}
