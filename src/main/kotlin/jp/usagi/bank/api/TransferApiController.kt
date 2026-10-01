package jp.usagi.bank.api

import java.security.Principal

import javax.validation.Valid

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

import jp.usagi.bank.api.dto.TransferRequest
import jp.usagi.bank.api.dto.TransferResponse
import jp.usagi.bank.service.TransferService

@RestController
@RequestMapping(value = ["/api/transfers"], produces = [MediaType.APPLICATION_JSON_UTF8_VALUE])
class TransferApiController(
    private val transferService: TransferService,
) {

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun transfer(@Valid @RequestBody req: TransferRequest, principal: Principal): TransferResponse {
        return TransferResponse.from(transferService.transfer(
                req.fromBranchCode, req.fromAccountNo,
                req.toBranchCode, req.toAccountNo,
                req.amount, req.description, principal.name))
    }
}
