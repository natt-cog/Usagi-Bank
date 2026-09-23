package jp.usagi.bank.api;

import java.security.Principal;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jp.usagi.bank.api.dto.TransferRequest;
import jp.usagi.bank.api.dto.TransferResponse;
import jp.usagi.bank.service.TransferService;

@RestController
@RequestMapping(value = "/api/transfers", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
public class TransferApiController {

    private final TransferService transferService;

    public TransferApiController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(@Valid @RequestBody TransferRequest req, Principal principal) {
        return TransferResponse.from(transferService.transfer(
                req.getFromBranchCode(), req.getFromAccountNo(),
                req.getToBranchCode(), req.getToAccountNo(),
                req.getAmount(), req.getDescription(), principal.getName()));
    }
}
