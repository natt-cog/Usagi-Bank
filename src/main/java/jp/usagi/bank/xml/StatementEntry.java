package jp.usagi.bank.xml;

import java.math.BigDecimal;
import java.util.Date;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlType(name = "entry", namespace = Statement.NS)
@XmlAccessorType(XmlAccessType.FIELD)
public class StatementEntry {

    @XmlElement(namespace = Statement.NS)
    @XmlJavaTypeAdapter(DateAdapter.class)
    private Date valueDate;

    @XmlElement(namespace = Statement.NS)
    private String type;

    @XmlElement(namespace = Statement.NS)
    private String description;

    @XmlElement(namespace = Statement.NS)
    private BigDecimal withdrawal;

    @XmlElement(namespace = Statement.NS)
    private BigDecimal deposit;

    @XmlElement(namespace = Statement.NS)
    private BigDecimal balance;

    @XmlElement(namespace = Statement.NS)
    private String referenceNo;

    public Date getValueDate() {
        return valueDate;
    }

    public void setValueDate(Date valueDate) {
        this.valueDate = valueDate;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getWithdrawal() {
        return withdrawal;
    }

    public void setWithdrawal(BigDecimal withdrawal) {
        this.withdrawal = withdrawal;
    }

    public BigDecimal getDeposit() {
        return deposit;
    }

    public void setDeposit(BigDecimal deposit) {
        this.deposit = deposit;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public void setReferenceNo(String referenceNo) {
        this.referenceNo = referenceNo;
    }
}
