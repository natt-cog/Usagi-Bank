package jp.usagi.bank.api.dto;

import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import jp.usagi.bank.domain.Account;

public class AccountDto {

    private Long id;
    private String branchCode;
    private String accountNo;
    private String accountType;
    private String accountTypeLabel;
    private String status;
    private String customerCifNo;
    private String customerName;
    private BigDecimal balance;
    private BigDecimal interestRate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Tokyo")
    private Date openedOn;

    public static AccountDto from(Account a) {
        AccountDto dto = new AccountDto();
        dto.id = a.getId();
        dto.branchCode = a.getBranchCode();
        dto.accountNo = a.getAccountNo();
        dto.accountType = a.getAccountType().name();
        dto.accountTypeLabel = a.getAccountType().getLabel();
        dto.status = a.getStatus().name();
        dto.customerCifNo = a.getCustomer().getCifNo();
        dto.customerName = a.getCustomer().getNameKanji();
        dto.balance = a.getBalance();
        dto.interestRate = a.getInterestRate();
        dto.openedOn = a.getOpenedOn();
        return dto;
    }

    public Long getId() { return id; }
    public String getBranchCode() { return branchCode; }
    public String getAccountNo() { return accountNo; }
    public String getAccountType() { return accountType; }
    public String getAccountTypeLabel() { return accountTypeLabel; }
    public String getStatus() { return status; }
    public String getCustomerCifNo() { return customerCifNo; }
    public String getCustomerName() { return customerName; }
    public BigDecimal getBalance() { return balance; }
    public BigDecimal getInterestRate() { return interestRate; }
    public Date getOpenedOn() { return openedOn; }
}
