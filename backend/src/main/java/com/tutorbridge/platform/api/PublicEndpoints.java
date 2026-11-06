package com.tutorbridge.platform.api;

import java.util.List;
import org.springframework.http.HttpMethod;

/**
 * Operations that need no session, such as register and login ({@code security: []} in the contract).
 * Every other request needs one. Each module declares its own as a bean, so the list sits next to
 * the controllers it opens.
 */
@FunctionalInterface
public interface PublicEndpoints {

    record Endpoint(HttpMethod method, String pathPattern) {}

    List<Endpoint> endpoints();
}
