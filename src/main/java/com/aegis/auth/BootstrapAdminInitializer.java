package com.aegis.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class BootstrapAdminInitializer {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    @Bean
    ApplicationRunner createBootstrapAdministrator(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            SecurityProperties securityProperties) {
        return args -> createIfMissing(userAccountRepository, passwordEncoder, securityProperties);
    }

    @Transactional
    void createIfMissing(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            SecurityProperties securityProperties) {
        String username = securityProperties.bootstrapAdmin().username();
        if (userAccountRepository.findByUsername(username).isPresent()) {
            return;
        }
        userAccountRepository.save(new UserAccount(
                username, passwordEncoder.encode(securityProperties.bootstrapAdmin().password()), Role.ADMIN));
        log.info("Created bootstrap administrator username={}", username);
    }
}
