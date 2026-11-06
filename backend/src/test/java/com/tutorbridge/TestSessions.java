package com.tutorbridge;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.SessionResolver;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Sessions for tests, standing in for the identity module's resolver (ADR-0010). Open a session for an
 * actor, then send requests as that actor:
 *
 * <pre>{@code
 * mvc.perform(get("/api/v1/students").with(sessions.as(TestSessions.actor(Role.PARENT))))
 * }</pre>
 */
public final class TestSessions implements SessionResolver {

    private final Map<String, Actor> live = new ConcurrentHashMap<>();

    public static Actor actor(Actor.Role role) {
        return new Actor(UUID.randomUUID(), role, UUID.randomUUID());
    }

    @Override
    public Optional<Actor> resolve(String token) {
        return Optional.ofNullable(live.get(token));
    }

    /** A token that resolves to the actor until revoked. */
    public String open(Actor actor) {
        String token = "test-session-" + UUID.randomUUID();
        live.put(token, actor);
        return token;
    }

    public void revoke(String token) {
        live.remove(token);
    }

    /** Sends the request with a bearer token for the actor, as the mobile app does. */
    public RequestPostProcessor as(Actor actor) {
        String token = open(actor);
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }
}
