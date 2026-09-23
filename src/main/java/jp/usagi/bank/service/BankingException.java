package jp.usagi.bank.service;

/** 業務例外の基底クラス. エラーコードは勘定系共通コード表 (UB-xxxx) に準拠. */
public class BankingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public BankingException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
