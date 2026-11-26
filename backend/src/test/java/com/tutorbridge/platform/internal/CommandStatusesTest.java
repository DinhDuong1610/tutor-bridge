package com.tutorbridge.platform.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.CommandResults;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommandStatusesTest {

    @Test
    void two_modules_answering_for_one_result_type_stop_the_application() {
        CommandResults first = results("NEED");
        CommandResults second = results("NEED");

        assertThatThrownBy(() -> new CommandStatuses(null, List.of(first, second), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("NEED");
    }

    private static CommandResults results(String type) {
        return new CommandResults() {
            @Override
            public Set<String> resultTypes() {
                return Set.of(type);
            }

            @Override
            public boolean visibleTo(Actor actor, String resultType, UUID resultId) {
                return true;
            }
        };
    }
}
