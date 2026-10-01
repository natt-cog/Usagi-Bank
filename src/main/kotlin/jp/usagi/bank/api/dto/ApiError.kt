package jp.usagi.bank.api.dto

import java.util.Date

class ApiError(
    val errorCode: String?,
    val message: String?,
) {
    val timestamp: Date = Date()
    val details: MutableList<String> = ArrayList()
}
