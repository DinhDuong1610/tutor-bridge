package com.tutorbridge.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.platform.api.Etag;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.WritePrecondition;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = WebFoundationTest.Probe.class)
@Import({WebFoundationTest.Probe.class, OpenProbes.class})
class WebFoundationTest {

    private static final String KEY = "k".repeat(16);

    @RestController
    static class Probe {

        record Body(String name, int count, Long total) {}

        @PostMapping("/probe/command")
        Map<String, String> command(IdempotencyKey key) {
            return Map.of("key", key.value());
        }

        @PostMapping("/probe/update")
        Map<String, Long> update(IdempotencyKey key, WritePrecondition.Expected expected) {
            return Map.of("version", expected.version());
        }

        @PostMapping("/probe/upsert")
        Map<String, String> upsert(IdempotencyKey key, WritePrecondition precondition) {
            return switch (precondition) {
                case WritePrecondition.CreateNew _ -> Map.of("mode", "create");
                case WritePrecondition.Expected e -> Map.of("mode", "update:" + e.version());
            };
        }

        @GetMapping("/probe/resource")
        ResponseEntity<Map<String, Instant>> resource() {
            return ResponseEntity.ok().eTag(Etag.of(7))
                    .body(Map.of("at", Instant.parse("2026-11-06T13:05:00Z")));
        }

        @PostMapping("/probe/body")
        Body body(@RequestBody Body body) {
            return body;
        }

        @GetMapping("/probe/list")
        Map<String, Integer> list(@RequestParam @Min(value = 1, message = "Tối thiểu 1.")
                @Max(value = 50, message = "Tối đa 50.") int limit) {
            return Map.of("limit", limit);
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void idempotency_key_reaches_the_controller_when_valid() throws Exception {
        mvc.perform(post("/probe/command").header(IdempotencyKey.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value(KEY));
        mvc.perform(post("/probe/command").header(IdempotencyKey.HEADER, "k".repeat(128)))
                .andExpect(status().isOk());
    }

    @Test
    void CR_014_missing_idempotency_key_is_400_before_the_controller() throws Exception {
        mvc.perform(post("/probe/command"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void out_of_range_idempotency_key_is_rejected_before_the_controller() throws Exception {
        for (var request : new org.springframework.test.web.servlet.RequestBuilder[] {
            post("/probe/command").header(IdempotencyKey.HEADER, ""),
            post("/probe/command").header(IdempotencyKey.HEADER, "k".repeat(15)),
            post("/probe/command").header(IdempotencyKey.HEADER, "k".repeat(129)),
        }) {
            mvc.perform(request)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_INVALID"));
        }
    }

    @Test
    void if_match_gives_the_expected_version() throws Exception {
        mvc.perform(post("/probe/update").header(IdempotencyKey.HEADER, KEY).header("If-Match", "\"42\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(42));
    }

    @Test
    void missing_if_match_is_428() throws Exception {
        mvc.perform(post("/probe/update").header(IdempotencyKey.HEADER, KEY))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));
    }

    @Test
    void weak_listed_or_malformed_if_match_is_412() throws Exception {
        for (String tag : new String[] {"W/\"42\"", "\"1\", \"2\"", "42", "\"-1\"", "*", "\"01\""}) {
            mvc.perform(post("/probe/update").header(IdempotencyKey.HEADER, KEY).header("If-Match", tag))
                    .andExpect(status().isPreconditionFailed())
                    .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        }
    }

    @Test
    void first_creation_uses_if_none_match_star() throws Exception {
        mvc.perform(post("/probe/upsert").header(IdempotencyKey.HEADER, KEY).header("If-None-Match", "*"))
                .andExpect(jsonPath("$.mode").value("create"));
        mvc.perform(post("/probe/upsert").header(IdempotencyKey.HEADER, KEY).header("If-Match", "\"3\""))
                .andExpect(jsonPath("$.mode").value("update:3"));
    }

    @Test
    void create_or_update_needs_exactly_one_condition() throws Exception {
        mvc.perform(post("/probe/upsert").header(IdempotencyKey.HEADER, KEY))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(post("/probe/upsert").header(IdempotencyKey.HEADER, KEY)
                        .header("If-None-Match", "*").header("If-Match", "\"3\""))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(post("/probe/upsert").header(IdempotencyKey.HEADER, KEY).header("If-None-Match", "\"3\""))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void responses_carry_private_no_store_including_errors() throws Exception {
        mvc.perform(get("/probe/resource"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("ETag", "\"7\""));
        mvc.perform(post("/probe/command"))
                .andExpect(header().string("Cache-Control", "private, no-store"));
        mvc.perform(get("/probe/missing"))
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }

    @Test
    void instants_are_iso_8601_strings() throws Exception {
        mvc.perform(get("/probe/resource"))
                .andExpect(jsonPath("$.at").value("2026-11-06T13:05:00Z"));
    }

    @Test
    void json_outside_the_contract_is_rejected() throws Exception {
        for (String json : new String[] {
            "{\"name\":\"a\",\"count\":1,\"role\":\"ADMIN\"}",
            "{\"name\":\"a\",\"count\":null}",
            "{\"name\":\"a\",\"count\":1} {}",
            // An integer field takes no fraction: 1.5 is refused, not cut to 1 (#168).
            "{\"name\":\"a\",\"count\":1.5}",
            "{\"name\":\"a\",\"count\":-0.5}",
            "{\"name\":\"a\",\"count\":1,\"total\":5.5}",
            "{\"name\":\"a\",\"count\":1e-1}",
            "{\"name\":\"a\",\"count\":3e9}",
        }) {
            mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"a\",\"count\":1}"))
                .andExpect(status().isOk());
    }

    /** JSON Schema's integer is any number whose fraction is zero (§6.1.1), so 8.0 and 8e0 are 8 (#168). */
    @Test
    void a_whole_number_written_with_a_fraction_or_an_exponent_is_an_integer() throws Exception {
        for (String count : new String[] {"8.0", "8e0", "0.8e1", "-8.00"}) {
            mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"a\",\"count\":" + count + ",\"total\":5.0}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(count.startsWith("-") ? -8 : 8))
                    .andExpect(jsonPath("$.total").value(5));
        }
    }

    @Test
    void parameter_constraints_become_field_problems() throws Exception {
        mvc.perform(get("/probe/list").param("limit", "51"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0].path").value("limit"))
                .andExpect(jsonPath("$.fields[0].message").value("Tối đa 50."));
    }

    @Test
    void etag_round_trips_and_rejects_negative_versions() {
        assertThat(Etag.parse(Etag.of(0))).isZero();
        assertThat(Etag.parse(Etag.of(123456789L))).isEqualTo(123456789L);
        assertThatThrownBy(() -> Etag.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
