package jp.usagi.bank.api.dto

import java.math.BigDecimal

import javax.validation.constraints.DecimalMin
import javax.validation.constraints.NotNull
import javax.validation.constraints.Pattern
import javax.validation.constraints.Size

class TransferRequest {

    @field:NotNull
    @field:Pattern(regexp = "\\d{3}", message = "店番は3桁の数字で入力してください")
    var fromBranchCode: String? = null

    @field:NotNull
    @field:Pattern(regexp = "\\d{7}", message = "口座番号は7桁の数字で入力してください")
    var fromAccountNo: String? = null

    @field:NotNull
    @field:Pattern(regexp = "\\d{3}", message = "店番は3桁の数字で入力してください")
    var toBranchCode: String? = null

    @field:NotNull
    @field:Pattern(regexp = "\\d{7}", message = "口座番号は7桁の数字で入力してください")
    var toAccountNo: String? = null

    @field:NotNull(message = "金額を入力してください")
    @field:DecimalMin(value = "1", message = "金額は1円以上を指定してください")
    var amount: BigDecimal? = null

    @field:Size(max = 40, message = "摘要は40文字以内で入力してください")
    var description: String? = null
}
