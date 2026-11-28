package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * #252 (Đính, 26/11/2026): the admin web calls the API from its server, so every admin shared the web
 * server's address as the source LoginThrottle counts. The web now sends the browser's address in
 * {@code X-TB-Client-IP}, and the API believes it only from an address in
 * {@code tutorbridge.security.trusted-web-proxies}. Checked on loginAdmin, where 5 failures from one source in
 * 15 minutes delay the next attempt: each failure here uses a new unknown email, so only the source counts.
 */
@TestPropertySource(properties = "tutorbridge.security.trusted-web-proxies=10.250.0.0/16,fd00:7b::/64")
class ClientAddressHttpTest extends HttpTest {

    private static final String HEADER = "X-TB-Client-IP";

    private static String random(String prefix) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return prefix + r.nextInt(256) + "." + r.nextInt(1, 255);
    }

    /** One failed admin login from {@code remote}, with {@code header} as X-TB-Client-IP when not null. */
    private int failedLogin(String remote, String header) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/auth/admin-login")
                .with(r -> {
                    r.setRemoteAddr(remote);
                    return r;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody-%s@example.test\",\"password\":\"not-the-password\"}"
                        .formatted(UUID.randomUUID()));
        if (header != null) {
            request.header(HEADER, header);
        }
        return mvc.perform(request).andExpect(Contract.matches()).andReturn().getResponse().getStatus();
    }

    @Test
    void from_the_trusted_web_server_two_browsers_are_counted_apart() throws Exception {
        String proxy = random("10.250.");
        String a = random("198.51.");
        String b = random("203.0.");
        for (int i = 0; i < 5; i++) {
            assertThat(failedLogin(proxy, a)).isEqualTo(401);
        }

        assertThat(failedLogin(proxy, a)).as("browser A, after its 5 failures").isEqualTo(429);
        assertThat(failedLogin(proxy, b)).as("browser B, behind the same web server").isEqualTo(401);
    }

    @Test
    void an_ipv6_browser_and_an_ipv6_web_server_work_too() throws Exception {
        String proxy = "fd00:7b::" + Integer.toHexString(ThreadLocalRandom.current().nextInt(1, 0xffff));
        String a = "2001:db8::" + Integer.toHexString(ThreadLocalRandom.current().nextInt(1, 0xffff));
        for (int i = 0; i < 5; i++) {
            assertThat(failedLogin(proxy, a)).isEqualTo(401);
        }

        assertThat(failedLogin(proxy, a)).isEqualTo(429);
        assertThat(failedLogin(proxy, random("192.0."))).isEqualTo(401);
    }

    @Test
    void from_any_other_address_the_header_is_ignored() throws Exception {
        String caller = random("10.251.");
        for (int i = 0; i < 5; i++) {
            // A new made-up browser address each time would dodge the count if it were believed.
            assertThat(failedLogin(caller, random("198.51."))).isEqualTo(401);
        }

        assertThat(failedLogin(caller, random("203.0."))).isEqualTo(429);
        assertThat(failedLogin(caller, null)).isEqualTo(429);
    }

    @Test
    void a_header_that_is_not_one_ip_address_counts_as_the_web_server_itself() throws Exception {
        String proxy = random("10.250.");
        // Never a name to look up, never a list: anything but one literal address is the proxy's own.
        List<String> bad = List.of("evil.example", "198.51.100.7, 203.0.113.9", "", "198.51.100.300",
                "localhost", "198.51.100.7:443");
        for (int i = 0; i < 5; i++) {
            assertThat(failedLogin(proxy, bad.get(i))).isEqualTo(401);
        }

        assertThat(failedLogin(proxy, bad.get(5))).isEqualTo(429);
        assertThat(failedLogin(proxy, null)).as("the web server's own address").isEqualTo(429);
        assertThat(failedLogin(proxy, random("198.51."))).as("a real browser behind it").isEqualTo(401);
    }

    @Test
    void no_controller_reads_the_remote_address_itself() throws IOException {
        // Every source the throttles count goes through ClientAddress, so none is left on the web server's address.
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            List<String> readers = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().replace('\\', '/').endsWith("platform/web/WebFoundation.java"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains("getRemoteAddr");
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .map(Path::toString).toList();
            assertThat(readers).isEmpty();
        }
    }
}
