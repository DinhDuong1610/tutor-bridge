package com.tutorbridge;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.LoggerFactory;

/**
 * Turns on DEBUG for Spring Boot's {@code web} logger group during one test, as {@code DEBUG=true} or
 * {@code logging.level.web=DEBUG} would. At that level Spring MVC logs every request body it reads, so a
 * test that a secret never reaches the log must run with it, or a leak shows only on a machine that
 * happens to set DEBUG (#197).
 */
public final class WebDebugLogging implements BeforeEachCallback, AfterEachCallback {

    /** Spring Boot's predefined {@code web} group. */
    private static final List<String> WEB = List.of("org.springframework.core.codec", "org.springframework.http",
            "org.springframework.web", "org.springframework.boot.actuate.endpoint.web",
            "org.springframework.boot.web.servlet.ServletContextInitializerBeans");

    private final Map<Logger, Level> before = new LinkedHashMap<>();

    @Override
    public void beforeEach(ExtensionContext context) {
        for (String name : WEB) {
            Logger logger = (Logger) LoggerFactory.getLogger(name);
            before.put(logger, logger.getLevel());
            logger.setLevel(Level.DEBUG);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        before.forEach(Logger::setLevel);
        before.clear();
    }
}
