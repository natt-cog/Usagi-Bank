package jp.usagi.bank.api.dto

import java.util.Date

import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("timestamp", "errorCode", "message", "details")
data class ApiError(val errorCode: String, val message: String?) {

    val timestamp: Date = Date()
    val details: MutableList<String> = ArrayList()
}
