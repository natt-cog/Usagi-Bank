package jp.usagi.bank.xml

import java.math.BigDecimal
import java.util.Date
import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlType
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter

@XmlType(name = "entry", namespace = Statement.NS)
@XmlAccessorType(XmlAccessType.FIELD)
class StatementEntry {
    @field:XmlElement(namespace = Statement.NS)
    @field:XmlJavaTypeAdapter(DateAdapter::class)
    var valueDate: Date? = null

    @field:XmlElement(namespace = Statement.NS)
    var type: String? = null

    @field:XmlElement(namespace = Statement.NS)
    var description: String? = null

    @field:XmlElement(namespace = Statement.NS)
    var withdrawal: BigDecimal? = null

    @field:XmlElement(namespace = Statement.NS)
    var deposit: BigDecimal? = null

    @field:XmlElement(namespace = Statement.NS)
    var balance: BigDecimal? = null

    @field:XmlElement(namespace = Statement.NS)
    var referenceNo: String? = null
}
