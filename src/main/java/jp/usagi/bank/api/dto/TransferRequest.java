package jp.usagi.bank.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class TransferRequest {

    @NotNull
    @Pattern(regexp = "\\d{3}", message = "店番は3桁の数字で入力してください")
    private String fromBranchCode;

    @NotNull
    @Pattern(regexp = "\\d{7}", message = "口座番号は7桁の数字で入力してください")
    private String fromAccountNo;

    @NotNull
    @Pattern(regexp = "\\d{3}", message = "店番は3桁の数字で入力してください")
    private String toBranchCode;

    @NotNull
    @Pattern(regexp = "\\d{7}", message = "口座番号は7桁の数字で入力してください")
    private String toAccountNo;

    @NotNull(message = "金額を入力してください")
    @DecimalMin(value = "1", message = "金額は1円以上を指定してください")
    private BigDecimal amount;

    @Size(max = 40, message = "摘要は40文字以内で入力してください")
    private String description;

    public String getFromBranchCode() { return fromBranchCode; }
    public void setFromBranchCode(String fromBranchCode) { this.fromBranchCode = fromBranchCode; }
    public String getFromAccountNo() { return fromAccountNo; }
    public void setFromAccountNo(String fromAccountNo) { this.fromAccountNo = fromAccountNo; }
    public String getToBranchCode() { return toBranchCode; }
    public void setToBranchCode(String toBranchCode) { this.toBranchCode = toBranchCode; }
    public String getToAccountNo() { return toAccountNo; }
    public void setToAccountNo(String toAccountNo) { this.toAccountNo = toAccountNo; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
