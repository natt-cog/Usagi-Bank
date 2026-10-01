package jp.usagi.bank.service

import org.joda.time.DateTimeConstants
import org.joda.time.DateTimeZone
import org.joda.time.LocalDate
import org.springframework.stereotype.Service
import java.util.Date

/**
 * 営業日管理.
 *
 * 本番では日次バッチが営業日テーブルを更新するが, 本システムでは
 * システム日付 (JST) を営業日として扱う. テストでは [override] で固定可能.
 */
@Service
class BusinessDateService {

    private var overridden: LocalDate? = null

    fun today(): LocalDate = overridden ?: LocalDate(JST)

    fun todayAsDate(): Date = today().toDate()

    /** 土日を除く翌営業日 (祝日カレンダー未対応). */
    fun nextBusinessDay(from: LocalDate): LocalDate {
        var d = from.plusDays(1)
        while (d.dayOfWeek == DateTimeConstants.SATURDAY || d.dayOfWeek == DateTimeConstants.SUNDAY) {
            d = d.plusDays(1)
        }
        return d
    }

    /** 利息決算日 (2月・8月の第3土曜日の翌営業日, 簡略化して 2/20・8/20 とする). */
    fun isInterestPostingDate(date: LocalDate): Boolean =
        (date.monthOfYear == 2 || date.monthOfYear == 8) && date.dayOfMonth == 20

    fun override(date: LocalDate) {
        overridden = date
    }

    fun clearOverride() {
        overridden = null
    }

    companion object {
        private val JST: DateTimeZone = DateTimeZone.forID("Asia/Tokyo")
    }
}
