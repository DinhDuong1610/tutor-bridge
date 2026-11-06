package com.tutorbridge;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.SessionResolver;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for HTTP tests over the whole application: real security chain, real PostgreSQL, sessions from
 * {@link TestSessions}. Check every response with {@link Contract#matches()}.
 *
 * <pre>{@code
 * Actor parent = newActor(Actor.Role.PARENT);
 * mvc.perform(get("/api/v1/students").with(sessions.as(parent)))
 *         .andExpect(status().isOk())
 *         .andExpect(Contract.matches());
 * }</pre>
 */
@AutoConfigureMockMvc
@Import(HttpTest.Sessions.class)
public abstract class HttpTest extends IntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Sessions {

        /** Primary over the identity module's resolver, so tests choose who is calling. */
        @Bean
        @Primary
        SessionResolver testSessions() {
            return new TestSessions();
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private SessionResolver resolver;

    protected TestSessions sessions() {
        return (TestSessions) resolver;
    }

    /** An active account with this role, as the actor of a fresh session. */
    protected Actor newActor(Actor.Role role) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into accounts(id, role, status) values (?, ?, 'ACTIVE')").params(id, role.name()).update();
        return new Actor(id, role, UUID.randomUUID());
    }
}
