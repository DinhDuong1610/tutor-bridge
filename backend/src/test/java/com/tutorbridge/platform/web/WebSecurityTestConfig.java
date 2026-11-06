package com.tutorbridge.platform.web;

import com.tutorbridge.TestSessions;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Brings the real security chain into a {@code @WebMvcTest} slice, with {@link TestSessions} as the
 * session resolver. Import it in any controller test: {@code @Import(WebSecurityTestConfig.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({SecurityConfig.class, ProblemWriter.class})
public class WebSecurityTestConfig {

    @Bean
    TestSessions testSessions() {
        return new TestSessions();
    }
}
