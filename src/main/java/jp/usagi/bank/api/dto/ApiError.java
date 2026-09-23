package jp.usagi.bank.api.dto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ApiError {

    private final Date timestamp = new Date();
    private final String errorCode;
    private final String message;
    private final List<String> details = new ArrayList<String>();

    public ApiError(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }

    public Date getTimestamp() { return timestamp; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public List<String> getDetails() { return details; }
}
