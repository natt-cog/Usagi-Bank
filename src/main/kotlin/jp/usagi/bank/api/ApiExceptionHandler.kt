package jp.usagi.bank.api

import jp.usagi.bank.api.dto.ApiError
import jp.usagi.bank.service.AccountNotFoundException
import jp.usagi.bank.service.BankingException
import jp.usagi.bank.service.CustomerNotFoundException
import jp.usagi.bank.service.InsufficientFundsException
import jp.usagi.bank.service.TransferLimitExceededException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["jp.usagi.bank.api"])
class ApiExceptionHandler {
    @ExceptionHandler(AccountNotFoundException::class, CustomerNotFoundException::class)
    fun notFound(e: BankingException): ResponseEntity<ApiError> = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError(e.errorCode, e.message))

    @ExceptionHandler(InsufficientFundsException::class, TransferLimitExceededException::class)
    fun businessRule(e: BankingException): ResponseEntity<ApiError> = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError(e.errorCode, e.message))

    @ExceptionHandler(BankingException::class)
    fun banking(e: BankingException): ResponseEntity<ApiError> = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError(e.errorCode, e.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(e: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val error = ApiError("UB-0001", "入力内容に誤りがあります")
        for (fe: FieldError in e.bindingResult.fieldErrors) {
            error.details.add("${fe.field}: ${fe.defaultMessage}")
        }
        return ResponseEntity.badRequest().body(error)
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception): ResponseEntity<ApiError> {
        log.error("予期しないエラー", e)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError("UB-9999", "システムエラーが発生しました"))
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ApiExceptionHandler::class.java)
    }
}
