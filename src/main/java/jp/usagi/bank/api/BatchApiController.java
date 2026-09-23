package jp.usagi.bank.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jp.usagi.bank.batch.EndOfDayJob;
import jp.usagi.bank.batch.EndOfDayJob.EodResult;
import jp.usagi.bank.service.BatchFileService;
import jp.usagi.bank.service.BatchFileService.ImportResult;

/** ホスト連携・バッチ手動起動 (管理者専用). */
@RestController
@RequestMapping("/api/batch")
public class BatchApiController {

    private final BatchFileService batchFileService;
    private final EndOfDayJob endOfDayJob;

    public BatchApiController(BatchFileService batchFileService, EndOfDayJob endOfDayJob) {
        this.batchFileService = batchFileService;
        this.endOfDayJob = endOfDayJob;
    }

    @GetMapping(value = "/accounts-file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> exportAccounts() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        batchFileService.exportAccounts(out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ACCOUNTS.DAT")
                .body(out.toByteArray());
    }

    @PostMapping(value = "/accrued-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ImportResult importAccrued(@RequestParam("file") MultipartFile file) throws IOException {
        return batchFileService.importAccrued(file.getInputStream());
    }

    @PostMapping(value = "/eod", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public EodResult runEndOfDay() {
        return endOfDayJob.run();
    }
}
