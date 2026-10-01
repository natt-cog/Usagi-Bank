package jp.usagi.bank.xml

import java.math.BigDecimal
import java.util.ArrayList
import java.util.Date

import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlElementWrapper
import javax.xml.bind.annotation.XmlRootElement
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter

/** 取引明細書 (XML). 他システム連携用に JAXB でシリアライズする. */
@XmlRootElement(name = "statement", namespace = Statement.NS)
@XmlAccessorType(XmlAccessType.FIELD)
class Statement {

    @field:XmlAttribute(name = "generatedAt")
    @field:XmlJavaTypeAdapter(DateTimeAdapter::class)
    var generatedAt: Date? = null

    @field:XmlElement(namespace = NS)
    var branchCode: String? = null

    @field:XmlElement(namespace = NS)
    var branchName: String? = null

    @field:XmlElement(namespace = NS)
    var accountNo: String? = null

    @field:XmlElement(namespace = NS)
    var accountType: String? = null

    @field:XmlElement(namespace = NS)
    var customerName: String? = null

    @field:XmlElement(namespace = NS)
    @field:XmlJavaTypeAdapter(DateAdapter::class)
    var periodFrom: Date? = null

    @field:XmlElement(namespace = NS)
    @field:XmlJavaTypeAdapter(DateAdapter::class)
    var periodTo: Date? = null

    @field:XmlElement(namespace = NS)
    var openingBalance: BigDecimal? = null

    @field:XmlElement(namespace = NS)
    var closingBalance: BigDecimal? = null

    @field:XmlElementWrapper(name = "entries", namespace = NS)
    @field:XmlElement(name = "entry", namespace = NS)
    var entries: MutableList<StatementEntry> = ArrayList()

    companion object {
        const val NS = "http://usagi.jp/bank/statement/1.0"
    }
}
