package com.tutorbridge.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.platform.api.Actor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * INV-20 across midnight (review of #152): a replacement that was written and is sent again with the
 * same key and body after 00:00 Asia/Ho_Chi_Minh answers the stored version, even though its windows
 * would now be refused as a new command. Real HTTP and PostgreSQL; only the clock is moved.
 */
@Import(TutorCalendarReplayHttpTest.MovableClockConfig.class)
class TutorCalendarReplayHttpTest extends HttpTest {

    private static final String PATH = "/api/v1/me/tutor-calendar";
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    /** A clock the test moves; the rest of the application reads it as its only clock. */
    static final class MovableClock extends Clock {

        private volatile Instant now;

        void set(Instant instant) {
            now = instant;
        }

        void reset() {
            now = null;
        }

        @Override
        public Instant instant() {
            Instant fixed = now;
            return fixed != null ? fixed : Instant.now();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MovableClockConfig {

        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }
    }

    @Autowired
    MovableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private ResultActions create(Actor tutor, String key, String json) throws Exception {
        return mvc.perform(put(PATH).with(sessions().as(tutor)).contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", key).header("If-None-Match", "*").content(json))
                .andExpect(Contract.matches());
    }

    private int versions(Actor tutor) {
        return jdbc.sql("select count(*) from calendars where owner_kind = 'TUTOR' and owner_id = ?")
                .param(tutor.accountId()).query(Integer.class).single();
    }

    @Test
    void inv_20_the_same_command_sent_again_after_midnight_answers_the_stored_version() throws Exception {
        LocalDate today = LocalDate.now(VIETNAM);
        ZonedDateTime start = today.atTime(18, 0).atZone(VIETNAM);
        String json = "{\"timezone\":\"Asia/Ho_Chi_Minh\",\"windows\":[{\"start\":\"%s\",\"end\":\"%s\",\"kind\":\"AVAILABLE\"}]}"
                .formatted(start.toInstant(), start.plusHours(1).toInstant());
        Actor tutor = newActor(Actor.Role.TUTOR);
        String key = UUID.randomUUID().toString();

        clock.set(today.atTime(23, 59).atZone(VIETNAM).toInstant());
        String id = create(tutor, key, json).andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        clock.set(today.plusDays(1).atTime(0, 1).atZone(VIETNAM).toInstant());
        create(tutor, key, json)
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.windows.length()").value(1));
        assertThat(versions(tutor)).isOne();

        // Control: the same body as a new command is now outside today..57 days and is refused.
        create(tutor, UUID.randomUUID().toString(), json)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(versions(tutor)).isOne();
    }
}
