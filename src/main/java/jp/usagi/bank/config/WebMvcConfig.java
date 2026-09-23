package jp.usagi.bank.config;

import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

@Configuration
public class WebMvcConfig extends WebMvcConfigurerAdapter {

    private final RequestTimingInterceptor requestTimingInterceptor;

    public WebMvcConfig(RequestTimingInterceptor requestTimingInterceptor) {
        this.requestTimingInterceptor = requestTimingInterceptor;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("redirect:/dashboard");
        registry.addViewController("/login").setViewName("login");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("/static/")
                .setCachePeriod(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestTimingInterceptor).addPathPatterns("/**");
    }

    @Bean
    public LocaleResolver localeResolver() {
        return new FixedLocaleResolver(Locale.JAPAN);
    }
}
