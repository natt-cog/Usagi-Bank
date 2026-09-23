package jp.usagi.bank.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jp.usagi.bank.service.BusinessDateService;
import jp.usagi.bank.service.InterestService;

/**
 * 日締めバッチ (オンライン閉局後 23:30 JST).
 *
 * 本番ではホスト側 JCL からの起動だが, 本システムではスケジューラで代替.
 */
@Component
public class EndOfDayJob {

    private static final Logger log = LoggerFactory.getLogger(EndOfDayJob.class);

    private final InterestService interestService;
    private final BusinessDateService businessDateService;
    private final boolean enabled;

    public EndOfDayJob(InterestService interestService, BusinessDateService businessDateService,
            @Value("${usagi.batch.eod.enabled:true}") boolean enabled) {
        this.interestService = interestService;
        this.businessDateService = businessDateService;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${usagi.batch.eod.cron:0 30 23 * * *}", zone = "Asia/Tokyo")
    public void runScheduled() {
        if (!enabled) {
            return;
        }
        run();
    }

    public EodResult run() {
        log.info("日締め開始 営業日={}", businessDateService.today());
        int accrued = interestService.accrueDaily();
        int posted = 0;
        if (businessDateService.isInterestPostingDate(businessDateService.today())) {
            posted = interestService.postInterest();
        }
        log.info("日締め終了 積数={} 決算={}", accrued, posted);
        return new EodResult(accrued, posted);
    }

    public static class EodResult {
        private final int accruedAccounts;
        private final int postedAccounts;

        public EodResult(int accruedAccounts, int postedAccounts) {
            this.accruedAccounts = accruedAccounts;
            this.postedAccounts = postedAccounts;
        }

        public int getAccruedAccounts() {
            return accruedAccounts;
        }

        public int getPostedAccounts() {
            return postedAccounts;
        }
    }
}
