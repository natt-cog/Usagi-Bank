package jp.usagi.bank.config

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter

/**
 * 応答時間ロギング. 勘定系SLA (オンライン 2秒以内) の監視用.
 */
@Component
class RequestTimingInterceptor : HandlerInterceptorAdapter() {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        request.setAttribute(START_ATTR, System.currentTimeMillis())
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        val start: Long = request.getAttribute(START_ATTR) as Long? ?: return
        val elapsed = System.currentTimeMillis() - start
        if (elapsed > SLA_MILLIS) {
            log.warn("SLA超過 {} {} {}ms", request.method, request.requestURI, elapsed)
        } else {
            log.debug("{} {} {}ms", request.method, request.requestURI, elapsed)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RequestTimingInterceptor::class.java)
        private const val START_ATTR = "usagi.requestStart"
        private const val SLA_MILLIS = 2000L
    }
}
