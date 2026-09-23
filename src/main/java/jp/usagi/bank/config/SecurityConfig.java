package jp.usagi.bank.config;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.web.PathPatternRequestMatcherBuilderFactoryBean;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 認証・認可設定.
 *
 * 窓口担当 (TELLER) / 管理者 (ADMIN) / 監査 (AUDITOR) の 3 ロール.
 * 行内 LDAP 連携は将来課題のためインメモリユーザで運用中.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/batch/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("TELLER", "ADMIN", "AUDITOR")
                .requestMatchers(HttpMethod.POST, "/api/transfers").hasAnyRole("TELLER", "ADMIN")
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                // JSP への forward / エラーページ (Spring Security 5 以前は認可対象外だった dispatch)
                .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                .requestMatchers("/static/**", "/webjars/**", "/login", "/health", "/manage/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/accounts/*/*/status").hasRole("ADMIN")
                .requestMatchers("/transfer/**").hasAnyRole("TELLER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/accounts/**", "/customers/**").hasAnyRole("TELLER", "ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll())
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    /** H2 コンソール等の複数サーブレット環境でもパスパターンで一意にマッチさせる. */
    @Bean
    public PathPatternRequestMatcherBuilderFactoryBean requestMatcherBuilder() {
        return new PathPatternRequestMatcherBuilderFactoryBean();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        // TODO: 本番は行内LDAPへ切替 (2015年度課題)
        return new InMemoryUserDetailsManager(
            User.withUsername("teller").password(passwordEncoder.encode("teller123")).roles("TELLER").build(),
            User.withUsername("admin").password(passwordEncoder.encode("admin123")).roles("ADMIN", "TELLER").build(),
            User.withUsername("auditor").password(passwordEncoder.encode("audit123")).roles("AUDITOR").build());
    }
}
