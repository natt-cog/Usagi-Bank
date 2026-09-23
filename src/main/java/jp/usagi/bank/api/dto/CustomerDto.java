package jp.usagi.bank.api.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import jp.usagi.bank.domain.Customer;

public class CustomerDto {

    private Long id;
    private String cifNo;
    private String nameKanji;
    private String nameKana;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Tokyo")
    private Date birthDate;
    private String postalCode;
    private String address;
    private String phone;
    private String kycStatus;

    public static CustomerDto from(Customer c) {
        CustomerDto dto = new CustomerDto();
        dto.id = c.getId();
        dto.cifNo = c.getCifNo();
        dto.nameKanji = c.getNameKanji();
        dto.nameKana = c.getNameKana();
        dto.birthDate = c.getBirthDate();
        dto.postalCode = c.getPostalCode();
        dto.address = c.getAddress();
        dto.phone = c.getPhone();
        dto.kycStatus = c.getKycStatus().name();
        return dto;
    }

    public Long getId() { return id; }
    public String getCifNo() { return cifNo; }
    public String getNameKanji() { return nameKanji; }
    public String getNameKana() { return nameKana; }
    public Date getBirthDate() { return birthDate; }
    public String getPostalCode() { return postalCode; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getKycStatus() { return kycStatus; }
}
