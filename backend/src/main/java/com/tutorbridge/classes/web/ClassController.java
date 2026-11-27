package com.tutorbridge.classes.web;

import com.tutorbridge.classes.internal.ClassService;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Etag;
import com.tutorbridge.platform.api.Page;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** getClass and listSessions (UC07, TB-20). The service checks who may read the class. */
@RestController
class ClassController {

    private final ClassService classes;

    ClassController(ClassService classes) {
        this.classes = classes;
    }

    @GetMapping("/api/v1/classes/{classId}")
    ResponseEntity<ClassService.ClassView> get(@AuthenticationPrincipal Actor actor, @PathVariable UUID classId) {
        ClassService.ClassView view = classes.get(actor, classId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Etag.of(view.revision())).body(view);
    }

    /** No ETag, as the other lists (listOffers, listNeeds): the contract declares it but does not require it. */
    @GetMapping("/api/v1/classes/{classId}/sessions")
    Page<ClassService.SessionView> sessions(@AuthenticationPrincipal Actor actor, @PathVariable UUID classId,
            @RequestParam(required = false) @Size(max = 500) String cursor,
            @RequestParam(required = false) Integer limit) {
        return classes.sessions(actor, classId, cursor, limit);
    }
}
