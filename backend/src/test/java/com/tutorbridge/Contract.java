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
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Checks a response against {@code contracts/openapi.yaml}: the operation exists, the status is
 * declared, the content type is declared and the body matches the schema (OpenAPI 3.1 schemas are
 * JSON Schema 2020-12). Use it on every HTTP test:
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
            return new Contract(YAMLMapper.builder().build().readTree(file.toFile()));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Cannot read the contract " + file.toAbsolutePath(), e);
        }
    }

    public static ResultMatcher matches() {
        return INSTANCE::check;
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
        if (!content.get(mediaKey).has("schema")) {
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
