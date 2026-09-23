package jp.usagi.bank.xml;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/** 取引明細書 (XML). 他システム連携用に JAXB でシリアライズする. */
@XmlRootElement(name = "statement", namespace = Statement.NS)
@XmlAccessorType(XmlAccessType.FIELD)
public class Statement {

    public static final String NS = "http://usagi.jp/bank/statement/1.0";

    @XmlAttribute(name = "generatedAt")
    @XmlJavaTypeAdapter(DateTimeAdapter.class)
    private Date generatedAt;

    @XmlElement(namespace = NS)
    private String branchCode;

    @XmlElement(namespace = NS)
    private String branchName;

    @XmlElement(namespace = NS)
    private String accountNo;

    @XmlElement(namespace = NS)
    private String accountType;

    @XmlElement(namespace = NS)
    private String customerName;

    @XmlElement(namespace = NS)
    @XmlJavaTypeAdapter(DateAdapter.class)
    private Date periodFrom;

    @XmlElement(namespace = NS)
    @XmlJavaTypeAdapter(DateAdapter.class)
    private Date periodTo;

    @XmlElement(namespace = NS)
    private BigDecimal openingBalance;

    @XmlElement(namespace = NS)
    private BigDecimal closingBalance;

    @XmlElementWrapper(name = "entries", namespace = NS)
    @XmlElement(name = "entry", namespace = NS)
    private List<StatementEntry> entries = new ArrayList<StatementEntry>();

    public Date getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Date generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public void setBranchCode(String branchCode) {
        this.branchCode = branchCode;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public void setAccountNo(String accountNo) {
        this.accountNo = accountNo;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Date getPeriodFrom() {
        return periodFrom;
    }

    public void setPeriodFrom(Date periodFrom) {
        this.periodFrom = periodFrom;
    }

    public Date getPeriodTo() {
        return periodTo;
    }

    public void setPeriodTo(Date periodTo) {
        this.periodTo = periodTo;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }

    public void setClosingBalance(BigDecimal closingBalance) {
        this.closingBalance = closingBalance;
    }

    public List<StatementEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<StatementEntry> entries) {
        this.entries = entries;
    }
}
