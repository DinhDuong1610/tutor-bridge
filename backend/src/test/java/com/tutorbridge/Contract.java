package com.tutorbridge;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks a response against {@code contracts/openapi.yaml}: the operation exists, the status is
 * declared, a declared {@code X-Operation-Id} is sent, the content type is declared and the body matches
 * the schema (OpenAPI 3.1 schemas are JSON Schema 2020-12). Use it on every HTTP test:
 *
 * <pre>{@code
 * mvc.perform(get("/api/v1/students").with(sessions.as(parent)))
 *         .andExpect(status().isOk())
 *         .andExpect(Contract.matches());
 * }</pre>
 */
public final class Contract {

    static final Path FILE = Path.of("../contracts/openapi.yaml");
    private static final String PREFIX = "/api/v1";
    private static final String IRI = "https://contract.tutorbridge.invalid/openapi.json";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String OPERATION_ID = "X-Operation-Id";
    private static final Pattern UUID_TEXT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static final Contract INSTANCE = load(FILE);

    private record Operation(String template, Pattern pattern, int params, JsonNode node, String pointer) {}

    private final JsonNode root;
    private final SchemaRegistry schemas;
    private final List<Operation> operations;

    private Contract(JsonNode root) {
        this.root = root;
        String json = JSON.writeValueAsString(root);
        this.schemas = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemas(Map.of(IRI, json)));
        this.operations = new ArrayList<>();
        for (var path : root.get("paths").properties()) {
            String template = path.getKey();
            for (var method : path.getValue().properties()) {
                String pointer = "/paths/" + escape(template) + "/" + method.getKey();
                operations.add(new Operation(method.getKey().toUpperCase(Locale.ROOT) + " " + template,
                        Pattern.compile(method.getKey().toUpperCase(Locale.ROOT) + " "
                                + template.replaceAll("\\{[^/]+}", "[^/]+")),
                        (int) template.chars().filter(c -> c == '{').count(), method.getValue(), pointer));
            }
        }
        // A literal segment wins over a parameter: /needs/drafts before /needs/{needId}.
        operations.sort(Comparator.comparingInt(Operation::params));
    }

    static Contract load(Path file) {
        try {
            // Jackson's YAML tree keeps an alias such as *id001 as its name; snakeyaml-engine resolves it.
            Object yaml = new org.snakeyaml.engine.v2.api.Load(
                    org.snakeyaml.engine.v2.api.LoadSettings.builder().setAllowRecursiveKeys(false)
                            .setMaxAliasesForCollections(1000).build())
                    .loadFromString(java.nio.file.Files.readString(file, StandardCharsets.UTF_8));
            return new Contract(JSON.valueToTree(yaml));
        } catch (RuntimeException | java.io.IOException e) {
            throw new IllegalStateException("Cannot read the contract " + file.toAbsolutePath(), e);
        }
    }

    public static ResultMatcher matches() {
        return INSTANCE::check;
    }

    /**
     * For an answer that came over a real socket, not through MockMvc (AT04 sends HTTP to two running
     * instances): copied into a mock response, then checked like {@link #matches()}.
     */
    public static void assertMatches(String method, String requestPath, java.net.http.HttpResponse<String> answer) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(answer.statusCode());
        answer.headers().map().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        answer.headers().firstValue("Content-Type").ifPresent(response::setContentType);
        try {
            response.getOutputStream().write(answer.body().getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        List<String> violations = INSTANCE.violations(method, requestPath, response);
        if (!violations.isEmpty()) {
            throw new AssertionError("Response breaks contracts/openapi.yaml:\n  " + String.join("\n  ", violations));
        }
    }

    /** The violations of one response; empty when it follows the contract. */
    List<String> violations(String method, String requestPath, MockHttpServletResponse response) {
        if (!requestPath.startsWith(PREFIX + "/")) {
            return List.of("Path is outside " + PREFIX + ": " + requestPath);
        }
        String key = method + " " + requestPath.substring(PREFIX.length());
        Optional<Operation> found = operations.stream().filter(o -> o.pattern().matcher(key).matches()).findFirst();
        if (found.isEmpty()) {
            return List.of("No operation in the contract for " + key);
        }
        Operation operation = found.get();
        String status = String.valueOf(response.getStatus());
        JsonNode responses = operation.node().path("responses");
        String statusKey = responses.has(status) ? status
                : responses.has(status.charAt(0) + "XX") ? status.charAt(0) + "XX"
                : responses.has("default") ? "default" : null;
        if (statusKey == null) {
            return List.of(operation.template() + " does not declare status " + status);
        }
        String responsePointer = operation.pointer() + "/responses/" + statusKey;
        JsonNode declared = responses.get(statusKey);
        if (declared.has("$ref")) {
            responsePointer = declared.get("$ref").asString().substring(1);
            declared = root.at(responsePointer);
        }

        // #221 (Đính, 28/11/2026): a declared X-Operation-Id is the receipt id the app resolves an unknown
        // outcome with (INV-24), so its absence is a violation. No other header is checked here.
        if (declared.path("headers").has(OPERATION_ID)) {
            String sent = response.getHeader(OPERATION_ID);
            if (sent == null || !UUID_TEXT.matcher(sent).matches()) {
                return List.of(operation.template() + " " + statusKey + " declares " + OPERATION_ID
                        + " but the answer sent " + (sent == null ? "none" : "'" + sent + "', not a uuid"));
            }
        }

        byte[] body = response.getContentAsByteArray();
        JsonNode content = declared.path("content");
        if (body.length == 0) {
            return List.of();
        }
        if (content.isMissingNode() || content.isEmpty()) {
            return List.of(operation.template() + " " + statusKey + " declares no body, but one was sent");
        }
        MediaType actual = MediaType.parseMediaType(Optional.ofNullable(response.getContentType()).orElse("application/octet-stream"));
        String mediaKey = null;
        for (var media : content.properties()) {
            if (MediaType.parseMediaType(media.getKey()).isCompatibleWith(actual)) {
                mediaKey = media.getKey();
                break;
            }
        }
        if (mediaKey == null) {
            return List.of(operation.template() + " " + statusKey + " does not declare content type " + actual);
        }
        // Bytes such as readAssignedEvidence (format: binary) have no JSON to validate; the declared media
        // type, checked above, is all the contract says about them.
        boolean json = actual.isCompatibleWith(MediaType.APPLICATION_JSON) || actual.getSubtype().endsWith("+json");
        if (!content.get(mediaKey).has("schema") || !json) {
            return List.of();
        }
        Schema schema = schemas.getSchema(SchemaLocation.of(
                IRI + "#" + responsePointer + "/content/" + escape(mediaKey) + "/schema"));
        JsonNode instance = JSON.readTree(new String(body, StandardCharsets.UTF_8));
        return schema.validate(instance).stream().map(Error::toString)
                .map(message -> operation.template() + " " + statusKey + ": " + message).toList();
    }

    private void check(MvcResult result) {
        var request = result.getRequest();
        List<String> violations = violations(request.getMethod(), request.getRequestURI(), result.getResponse());
        if (!violations.isEmpty()) {
            throw new AssertionError("Response breaks contracts/openapi.yaml:\n  " + String.join("\n  ", violations));
        }
    }

    /** JSON Pointer escaping (RFC 6901). */
    private static String escape(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }
}
