package com.tutorbridge.identity.internal;

import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Runs {@link AdminAccounts#setUp} once and stops the application (TB-13). Only present when the operator
 * names an email:
 *
 * <pre>
 * TB_ADMIN_SETUP_PASSWORD='...' ./gradlew bootRun --args='--tutorbridge.admin-setup.email=admin@example.test
 *     --tutorbridge.admin-setup.output=D:/tb-tmp/admin-otpauth.txt'
 * </pre>
 *
 * The password comes from the environment, never the command line, which shells keep in their history.
 * The log names the account and the file, never the secret or the URI.
 */
@Component
@ConditionalOnProperty(prefix = "tutorbridge.admin-setup", name = "email")
class AdminSetupRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminSetupRunner.class);

    private final AdminAccounts accounts;
    private final ConfigurableApplicationContext context;
    private final String email;
    private final String password;
    private final String output;

    AdminSetupRunner(AdminAccounts accounts, ConfigurableApplicationContext context,
            @Value("${tutorbridge.admin-setup.email}") String email,
            @Value("${TB_ADMIN_SETUP_PASSWORD:}") String password,
            @Value("${tutorbridge.admin-setup.output:}") String output) {
        this.accounts = accounts;
        this.context = context;
        this.email = email;
        this.password = password;
        this.output = output;
    }

    @Override
    public void run(ApplicationArguments args) {
        int code = 0;
        try {
            if (output.isBlank()) {
                throw new IllegalArgumentException("admin-setup: name the file with --tutorbridge.admin-setup.output");
            }
            UUID id = accounts.setUp(email, password, Path.of(output));
            LOG.info("admin-setup: ADMIN {} has a new password, TOTP enrollment and VERIFY; scan the URI in {} "
                    + "with an authenticator app, then delete the file", id, output);
        } catch (IllegalArgumentException e) {
            LOG.error(e.getMessage());
            code = 2;
        }
        int exit = code;
        System.exit(SpringApplication.exit(context, () -> exit));
    }
}
