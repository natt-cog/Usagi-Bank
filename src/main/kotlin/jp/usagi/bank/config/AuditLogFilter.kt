package jp.usagi.bank.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import javax.servlet.FilterChain
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 監査ログフィルタ. 金融庁検査対応のため全 API 呼出を記録する.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuditLogFilter : OncePerRequestFilter() {

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        try {
            chain.doFilter(request, response)
        } finally {
            if (request.requestURI.contains("/api/")) {
                val fmt = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
                val user: String = request.remoteUser ?: "-"
                audit.info(
                    "{}\t{}\t{}\t{}\t{}\t{}",
                    fmt.format(Date()),
                    request.remoteAddr,
                    user,
                    request.method,
                    request.requestURI,
                    response.status,
                )
            }
        }
    }

    companion object {
        private val audit: Logger = LoggerFactory.getLogger("AUDIT")
    }
}
