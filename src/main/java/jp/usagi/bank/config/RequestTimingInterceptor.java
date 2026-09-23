package jp.usagi.bank.config;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

/**
 * 応答時間ロギング. 勘定系SLA (オンライン 2秒以内) の監視用.
 */
@Component
public class RequestTimingInterceptor extends HandlerInterceptorAdapter {

    private static final Logger log = LoggerFactory.getLogger(RequestTimingInterceptor.class);
    private static final String START_ATTR = "usagi.requestStart";
    private static final long SLA_MILLIS = 2000L;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Long start = (Long) request.getAttribute(START_ATTR);
        if (start == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - start.longValue();
        if (elapsed > SLA_MILLIS) {
            log.warn("SLA超過 {} {} {}ms", request.getMethod(), request.getRequestURI(), elapsed);
        } else {
            log.debug("{} {} {}ms", request.getMethod(), request.getRequestURI(), elapsed);
        }
    }
}
