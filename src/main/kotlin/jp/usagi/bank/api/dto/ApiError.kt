package jp.usagi.bank.api.dto

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import java.util.Date

@JsonPropertyOrder("timestamp", "errorCode", "message", "details")
data class ApiError(val errorCode: String, val message: String?) {

    val timestamp: Date = Date()
    val details: MutableList<String> = ArrayList()
}
