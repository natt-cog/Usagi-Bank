package jp.usagi.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * うさぎ銀行 勘定系オンラインシステム エントリポイント.
 *
 * WebSphere / Tomcat への WAR デプロイと java -jar 起動の両方に対応する.
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class UsagiBankApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(UsagiBankApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(UsagiBankApplication.class);
    }
}
