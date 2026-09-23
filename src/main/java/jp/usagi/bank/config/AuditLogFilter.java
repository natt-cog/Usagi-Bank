package jp.usagi.bank.config;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 監査ログフィルタ. 金融庁検査対応のため全 API 呼出を記録する.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditLogFilter extends OncePerRequestFilter {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            if (request.getRequestURI().contains("/api/")) {
                SimpleDateFormat fmt = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                String user = request.getRemoteUser() == null ? "-" : request.getRemoteUser();
                audit.info("{}\t{}\t{}\t{}\t{}\t{}", fmt.format(new Date()), request.getRemoteAddr(), user,
                        request.getMethod(), request.getRequestURI(), response.getStatus());
            }
        }
    }
}
