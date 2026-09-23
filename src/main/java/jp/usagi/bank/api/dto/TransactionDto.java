package jp.usagi.bank.api.dto;

import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import jp.usagi.bank.domain.Transaction;

public class TransactionDto {

    private Long id;
    private String type;
    private String typeLabel;
    private boolean credit;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String description;
    private String referenceNo;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Tokyo")
    private Date valueDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Tokyo")
    private Date postedAt;

    public static TransactionDto from(Transaction t) {
        TransactionDto dto = new TransactionDto();
        dto.id = t.getId();
        dto.type = t.getType().name();
        dto.typeLabel = t.getType().getLabel();
        dto.credit = t.getType().isCredit();
        dto.amount = t.getAmount();
        dto.balanceAfter = t.getBalanceAfter();
        dto.description = t.getDescription();
        dto.referenceNo = t.getReferenceNo();
        dto.valueDate = t.getValueDate();
        dto.postedAt = t.getPostedAt();
        return dto;
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getTypeLabel() { return typeLabel; }
    public boolean isCredit() { return credit; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getDescription() { return description; }
    public String getReferenceNo() { return referenceNo; }
    public Date getValueDate() { return valueDate; }
    public Date getPostedAt() { return postedAt; }
}
