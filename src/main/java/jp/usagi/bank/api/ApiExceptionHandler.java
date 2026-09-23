package jp.usagi.bank.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jp.usagi.bank.api.dto.ApiError;
import jp.usagi.bank.service.AccountNotFoundException;
import jp.usagi.bank.service.BankingException;
import jp.usagi.bank.service.CustomerNotFoundException;
import jp.usagi.bank.service.InsufficientFundsException;
import jp.usagi.bank.service.TransferLimitExceededException;

@RestControllerAdvice(basePackages = "jp.usagi.bank.api")
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({ AccountNotFoundException.class, CustomerNotFoundException.class })
    public ResponseEntity<ApiError> notFound(BankingException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler({ InsufficientFundsException.class, TransferLimitExceededException.class })
    public ResponseEntity<ApiError> businessRule(BankingException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ApiError(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(BankingException.class)
    public ResponseEntity<ApiError> banking(BankingException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        ApiError error = new ApiError("UB-0001", "入力内容に誤りがあります");
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            error.getDetails().add(fe.getField() + ": " + fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("予期しないエラー", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("UB-9999", "システムエラーが発生しました"));
    }
}
