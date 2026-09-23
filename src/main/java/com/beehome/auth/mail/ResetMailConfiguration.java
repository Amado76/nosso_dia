package com.beehome.auth.mail;

import com.beehome.auth.security.AuthProperties;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "auth.reset-mail.enabled", havingValue = "true")
@EnableConfigurationProperties(ResetMailProperties.class)
class ResetMailConfiguration {
    @Bean
    ThreadPoolTaskExecutor passwordResetMailExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("password-reset-mail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        return executor;
    }

    @Bean
    PasswordResetDelivery smtpPasswordResetDelivery(JavaMailSender sender,
            @Qualifier("passwordResetMailExecutor") ThreadPoolTaskExecutor executor,
            ResetMailProperties properties, AuthProperties auth,
            @org.springframework.beans.factory.annotation.Value("${spring.mail.host:}") String host) {
        if (host.isBlank()) throw new IllegalArgumentException("MAIL_HOST is required when reset email is enabled");
        return new SmtpPasswordResetDelivery(sender, executor, properties, auth.resetTtl());
    }
}
