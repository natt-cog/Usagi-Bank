package jp.usagi.bank.web;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

import jp.usagi.bank.service.AccountNotFoundException;
import jp.usagi.bank.service.CustomerNotFoundException;

@ControllerAdvice(basePackages = "jp.usagi.bank.web")
public class WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    @ExceptionHandler({ AccountNotFoundException.class, CustomerNotFoundException.class })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView notFound(HttpServletRequest request, RuntimeException e) {
        log.warn("404 {} : {}", request.getRequestURI(), e.getMessage());
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", 404);
        mav.addObject("message", e.getMessage());
        return mav;
    }
}
