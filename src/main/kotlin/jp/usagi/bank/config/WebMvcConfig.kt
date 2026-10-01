package jp.usagi.bank.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter
import org.springframework.web.servlet.i18n.FixedLocaleResolver
import java.util.Locale

@Configuration
class WebMvcConfig(private val requestTimingInterceptor: RequestTimingInterceptor) : WebMvcConfigurerAdapter() {

    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addViewController("/").setViewName("redirect:/dashboard")
        registry.addViewController("/login").setViewName("login")
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/static/**")
            .addResourceLocations("/static/")
            .setCachePeriod(3600)
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(requestTimingInterceptor).addPathPatterns("/**")
    }

    @Bean
    fun localeResolver(): LocaleResolver {
        return FixedLocaleResolver(Locale.JAPAN)
    }
}
