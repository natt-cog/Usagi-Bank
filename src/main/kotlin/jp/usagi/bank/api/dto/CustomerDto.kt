package jp.usagi.bank.api.dto

import com.fasterxml.jackson.annotation.JsonFormat
import jp.usagi.bank.domain.Customer
import java.util.Date

data class CustomerDto(
    val id: Long?,
    val cifNo: String?,
    val nameKanji: String?,
    val nameKana: String?,
    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Tokyo")
    val birthDate: Date?,
    val postalCode: String?,
    val address: String?,
    val phone: String?,
    val kycStatus: String?,
) {
    companion object {
        @JvmStatic
        fun from(c: Customer): CustomerDto =
            CustomerDto(
                id = c.id,
                cifNo = c.cifNo,
                nameKanji = c.nameKanji,
                nameKana = c.nameKana,
                birthDate = c.birthDate,
                postalCode = c.postalCode,
                address = c.address,
                phone = c.phone,
                kycStatus = c.kycStatus.name,
            )
    }
}
