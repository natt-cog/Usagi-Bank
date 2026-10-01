package jp.usagi.bank.batch

import jp.usagi.bank.service.BusinessDateService
import jp.usagi.bank.service.InterestService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 日締めバッチ (オンライン閉局後 23:30 JST).
 *
 * 本番ではホスト側 JCL からの起動だが, 本システムではスケジューラで代替.
 */
@Component
class EndOfDayJob(
    private val interestService: InterestService,
    private val businessDateService: BusinessDateService,
    @Value("\${usagi.batch.eod.enabled:true}") private val enabled: Boolean,
) {
    @Scheduled(cron = "\${usagi.batch.eod.cron:0 30 23 * * *}", zone = "Asia/Tokyo")
    fun runScheduled() {
        if (!enabled) {
            return
        }
        run()
    }

    fun run(): EodResult {
        log.info("日締め開始 営業日={}", businessDateService.today())
        val accrued = interestService.accrueDaily()
        var posted = 0
        if (businessDateService.isInterestPostingDate(businessDateService.today())) {
            posted = interestService.postInterest()
        }
        log.info("日締め終了 積数={} 決算={}", accrued, posted)
        return EodResult(accrued, posted)
    }

    class EodResult(
        val accruedAccounts: Int,
        val postedAccounts: Int,
    )

    companion object {
        private val log: Logger = LoggerFactory.getLogger(EndOfDayJob::class.java)
    }
}
