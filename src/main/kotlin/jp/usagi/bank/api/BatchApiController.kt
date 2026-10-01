package jp.usagi.bank.api

import java.io.ByteArrayOutputStream
import java.io.IOException

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

import jp.usagi.bank.batch.EndOfDayJob
import jp.usagi.bank.batch.EndOfDayJob.EodResult
import jp.usagi.bank.service.BatchFileService
import jp.usagi.bank.service.BatchFileService.ImportResult

/** ホスト連携・バッチ手動起動 (管理者専用). */
@RestController
@RequestMapping("/api/batch")
class BatchApiController(
    private val batchFileService: BatchFileService,
    private val endOfDayJob: EndOfDayJob
) {

    @GetMapping(value = ["/accounts-file"], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    @Throws(IOException::class)
    fun exportAccounts(): ResponseEntity<ByteArray> {
        val out = ByteArrayOutputStream()
        batchFileService.exportAccounts(out)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ACCOUNTS.DAT")
            .body(out.toByteArray())
    }

    @PostMapping(
        value = ["/accrued-file"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_UTF8_VALUE]
    )
    @Throws(IOException::class)
    fun importAccrued(@RequestParam("file") file: MultipartFile): ImportResult =
        batchFileService.importAccrued(file.inputStream)

    @PostMapping(value = ["/eod"], produces = [MediaType.APPLICATION_JSON_UTF8_VALUE])
    fun runEndOfDay(): EodResult = endOfDayJob.run()
}
