package com.tutorbridge.platform.web;

import com.tutorbridge.platform.api.PublicEndpoints;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;

/** Opens {@code /probe/**} for tests of the web layer that are not about sessions. */
@TestConfiguration(proxyBeanMethods = false)
@Import(WebSecurityTestConfig.class)
class OpenProbes {

    @Bean
    PublicEndpoints openProbes() {
        return () -> List.of(
                new PublicEndpoints.Endpoint(HttpMethod.GET, "/probe/**"),
                new PublicEndpoints.Endpoint(HttpMethod.POST, "/probe/**"));
    }
}
