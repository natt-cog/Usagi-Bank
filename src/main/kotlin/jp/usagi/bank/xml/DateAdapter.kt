package jp.usagi.bank.xml

import java.text.SimpleDateFormat
import java.util.Date
import javax.xml.bind.annotation.adapters.XmlAdapter

/** yyyy-MM-dd 形式. SimpleDateFormat はスレッドセーフでないため都度生成. */
class DateAdapter : XmlAdapter<String, Date>() {

    @Throws(Exception::class)
    override fun unmarshal(v: String?): Date? = if (v == null) null else SimpleDateFormat("yyyy-MM-dd").parse(v)

    override fun marshal(v: Date?): String? = if (v == null) null else SimpleDateFormat("yyyy-MM-dd").format(v)
}
