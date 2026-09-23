package jp.usagi.bank.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import org.springframework.stereotype.Service;

/**
 * 営業日管理.
 *
 * 本番では日次バッチが営業日テーブルを更新するが, 本システムでは
 * システム日付 (JST) を営業日として扱う. テストでは {@link #override(LocalDate)} で固定可能.
 */
@Service
public class BusinessDateService {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private LocalDate overridden;

    public LocalDate today() {
        return overridden != null ? overridden : LocalDate.now(JST);
    }

    /** 営業日を JVM デフォルトタイムゾーンの 0 時として {@link Date} 化する (Joda LocalDate#toDate 互換). */
    public Date todayAsDate() {
        return toDate(today());
    }

    public static Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /** 土日を除く翌営業日 (祝日カレンダー未対応). */
    public LocalDate nextBusinessDay(LocalDate from) {
        LocalDate d = from.plusDays(1);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** 利息決算日 (2月・8月の第3土曜日の翌営業日, 簡略化して 2/20・8/20 とする). */
    public boolean isInterestPostingDate(LocalDate date) {
        return (date.getMonthValue() == 2 || date.getMonthValue() == 8) && date.getDayOfMonth() == 20;
    }

    public void override(LocalDate date) {
        this.overridden = date;
    }

    public void clearOverride() {
        this.overridden = null;
    }
}
