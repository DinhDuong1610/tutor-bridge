package com.tutorbridge.identity;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.Contract;
import com.tutorbridge.IntegrationTest;
import com.tutorbridge.identity.internal.PasswordHasher;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Issue #140: resetPassword hashed the new password (Argon2id, about 19 MiB) before checking the
 * token, and /auth/reset had no rate limit, so bogus tokens could exhaust memory. A separate class
 * because the hasher spy changes the Spring context.
 */
@AutoConfigureMockMvc
class PasswordResetCostHttpTest extends IntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NEW = "mot-mat-khau-moi-du-dai";

    @Autowired
    private MockMvc mvc;

    @MockitoSpyBean
    private PasswordHasher hasher;

    @Test
    void a_token_that_was_never_issued_is_refused_without_hashing_the_password() throws Exception {
        reset(bogusToken(), randomIp())
                .andExpect(status().isBadRequest())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("LINK_INVALID"));

        verify(hasher, never()).hash(anyString());
    }

    @Test
    void resets_share_the_recovery_limit_per_source() throws Exception {
        // recovery-per-source: 10 per hour (application.yaml); a reset counts against it (Huy, 13/11/2026).
        String ip = randomIp();
        for (int i = 0; i < 10; i++) {
            reset(bogusToken(), ip).andExpect(status().isBadRequest());
        }

        reset(bogusToken(), ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
        verify(hasher, never()).hash(anyString());
    }

    private ResultActions reset(String token, String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/reset")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("token", token, "newPassword", NEW))));
    }

    private static String bogusToken() {
        return "khong-phai-token-" + UUID.randomUUID();
    }

    private static String randomIp() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
    }
}
