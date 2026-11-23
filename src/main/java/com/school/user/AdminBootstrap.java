package com.school.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the initial ADMIN account at startup when no ADMIN exists and ADMIN_PASSWORD is provided.
 */
@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_EMAIL = "admin@school.edu";
    public static final String ADMIN_FULL_NAME = "Nasima Akter";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public AdminBootstrap(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.admin-password:}") String adminPassword
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (appUserRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            log.debug("Skipping initial admin bootstrap because ADMIN_PASSWORD is not set");
            return;
        }
        AppUser admin = new AppUser(
                ADMIN_USERNAME,
                ADMIN_EMAIL,
                passwordEncoder.encode(adminPassword),
                ADMIN_FULL_NAME,
                Role.ADMIN
        );
        appUserRepository.save(admin);
        log.info("Bootstrapped initial ADMIN account '{}'", ADMIN_USERNAME);
    }
}
