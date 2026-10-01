package jp.usagi.bank.xml

import java.text.SimpleDateFormat
import java.util.Date

import javax.xml.bind.annotation.adapters.XmlAdapter

class DateTimeAdapter : XmlAdapter<String?, Date?>() {

    @Throws(Exception::class)
    override fun unmarshal(v: String?): Date? = if (v == null) null else SimpleDateFormat(PATTERN).parse(v)

    override fun marshal(v: Date?): String? = if (v == null) null else SimpleDateFormat(PATTERN).format(v)

    companion object {
        private const val PATTERN = "yyyy-MM-dd'T'HH:mm:ssXXX"
    }
}
