package jp.usagi.bank.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

/**
 * 認証・認可設定.
 *
 * 窓口担当 (TELLER) / 管理者 (ADMIN) / 監査 (AUDITOR) の 3 ロール.
 * 行内 LDAP 連携は将来課題のためインメモリユーザで運用中.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Configuration
    @Order(1)
    public static class ApiSecurityConfig extends WebSecurityConfigurerAdapter {

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.antMatcher("/api/**")
                .csrf().disable()
                .authorizeRequests()
                    .antMatchers("/api/batch/**").hasRole("ADMIN")
                    .antMatchers(HttpMethod.GET, "/api/**").hasAnyRole("TELLER", "ADMIN", "AUDITOR")
                    .antMatchers(HttpMethod.POST, "/api/transfers").hasAnyRole("TELLER", "ADMIN")
                    .anyRequest().authenticated()
                .and()
                .httpBasic();
        }
    }

    @Configuration
    @Order(2)
    public static class WebSecurityConfig extends WebSecurityConfigurerAdapter {

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.authorizeRequests()
                    .antMatchers("/static/**", "/webjars/**", "/login", "/health").permitAll()
                    .antMatchers("/admin/**").hasRole("ADMIN")
                    .antMatchers("/transfer/**").hasAnyRole("TELLER", "ADMIN")
                    .anyRequest().authenticated()
                .and()
                .formLogin()
                    .loginPage("/login")
                    .defaultSuccessUrl("/dashboard", true)
                    .failureUrl("/login?error")
                    .permitAll()
                .and()
                .logout()
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/login?logout")
                    .permitAll()
                .and()
                .headers().frameOptions().sameOrigin();
        }
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        // TODO: 本番は行内LDAPへ切替 (2015年度課題)
        auth.inMemoryAuthentication()
            .withUser("teller").password("teller123").roles("TELLER")
            .and()
            .withUser("admin").password("admin123").roles("ADMIN", "TELLER")
            .and()
            .withUser("auditor").password("audit123").roles("AUDITOR");
    }
}
