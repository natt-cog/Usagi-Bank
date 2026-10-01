package jp.usagi.bank.web

import jp.usagi.bank.service.AccountNotFoundException
import jp.usagi.bank.service.CustomerNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest

@ControllerAdvice(basePackages = ["jp.usagi.bank.web"])
class WebExceptionHandler {
    @ExceptionHandler(AccountNotFoundException::class, CustomerNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(
        request: HttpServletRequest,
        e: RuntimeException,
    ): ModelAndView {
        log.warn("404 {} : {}", request.requestURI, e.message)
        val mav = ModelAndView("error")
        mav.addObject("status", 404)
        mav.addObject("message", e.message)
        return mav
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(WebExceptionHandler::class.java)
    }
}
