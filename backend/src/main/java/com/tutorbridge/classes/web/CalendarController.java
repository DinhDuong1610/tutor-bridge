package com.tutorbridge.classes.web;

import com.tutorbridge.classes.internal.CalendarService;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Etag;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.WritePrecondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * getTutorCalendar, replaceTutorCalendar, getStudentCalendar, replaceStudentCalendar (UC07, TB-15).
 * The service checks the owner and the windows.
 */
@RestController
class CalendarController {

    // The service checks the timezone, the window count and each kind, so every problem names its field.
    record WindowInput(@NotNull Instant start, @NotNull Instant end, @NotNull @Size(max = 20) String kind) {}

    record CalendarInput(@NotNull @Size(max = 100) String timezone,
            @NotNull @Size(max = 1000) List<@NotNull @Valid WindowInput> windows) {}

    private final CalendarService calendars;

    CalendarController(CalendarService calendars) {
        this.calendars = calendars;
    }

    @GetMapping("/api/v1/me/tutor-calendar")
    ResponseEntity<CalendarService.Calendar> getTutorCalendar(@AuthenticationPrincipal Actor actor) {
        return withEtag(calendars.getTutorCalendar(actor));
    }

    @PutMapping("/api/v1/me/tutor-calendar")
    ResponseEntity<CalendarService.Calendar> replaceTutorCalendar(@AuthenticationPrincipal Actor actor,
            IdempotencyKey key, WritePrecondition precondition, @Valid @RequestBody CalendarInput input) {
        return withEtag(calendars.replaceTutorCalendar(actor, key, precondition, input.timezone(), windows(input)));
    }

    @GetMapping("/api/v1/students/{studentId}/calendar")
    ResponseEntity<CalendarService.Calendar> getStudentCalendar(@AuthenticationPrincipal Actor actor,
            @PathVariable UUID studentId) {
        return withEtag(calendars.getStudentCalendar(actor, studentId));
    }

    @PutMapping("/api/v1/students/{studentId}/calendar")
    ResponseEntity<CalendarService.Calendar> replaceStudentCalendar(@AuthenticationPrincipal Actor actor,
            @PathVariable UUID studentId, IdempotencyKey key, WritePrecondition precondition,
            @Valid @RequestBody CalendarInput input) {
        return withEtag(calendars.replaceStudentCalendar(actor, studentId, key, precondition, input.timezone(),
                windows(input)));
    }

    private static List<CalendarService.Window> windows(CalendarInput input) {
        return input.windows().stream().map(w -> new CalendarService.Window(w.start(), w.end(), w.kind())).toList();
    }

    private static ResponseEntity<CalendarService.Calendar> withEtag(CalendarService.Calendar calendar) {
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Etag.of(calendar.version())).body(calendar);
    }
}
