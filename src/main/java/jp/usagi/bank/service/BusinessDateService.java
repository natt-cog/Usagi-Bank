package jp.usagi.bank.service;

import java.util.Date;

import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * 営業日管理.
 *
 * 本番では日次バッチが営業日テーブルを更新するが, 本システムでは
 * システム日付 (JST) を営業日として扱う. テストでは {@link #override(LocalDate)} で固定可能.
 */
@Service
public class BusinessDateService {

    private static final DateTimeZone JST = DateTimeZone.forID("Asia/Tokyo");

    private LocalDate overridden;

    public LocalDate today() {
        return overridden != null ? overridden : new LocalDate(JST);
    }

    public Date todayAsDate() {
        return today().toDate();
    }

    /** 土日を除く翌営業日 (祝日カレンダー未対応). */
    public LocalDate nextBusinessDay(LocalDate from) {
        LocalDate d = from.plusDays(1);
        while (d.getDayOfWeek() == DateTimeConstants.SATURDAY || d.getDayOfWeek() == DateTimeConstants.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** 利息決算日 (2月・8月の第3土曜日の翌営業日, 簡略化して 2/20・8/20 とする). */
    public boolean isInterestPostingDate(LocalDate date) {
        return (date.getMonthOfYear() == 2 || date.getMonthOfYear() == 8) && date.getDayOfMonth() == 20;
    }

    public void override(LocalDate date) {
        this.overridden = date;
    }

    public void clearOverride() {
        this.overridden = null;
    }
}
