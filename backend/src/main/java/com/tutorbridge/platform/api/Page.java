package com.tutorbridge.platform.api;

import java.util.List;
import java.util.function.Function;
import org.springframework.http.HttpStatus;

/** The items and nextCursor shape shared by every list in the OpenAPI contract. */
public record Page<T>(List<T> items, String nextCursor) {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    public static int limit(Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        if (requested < 1 || requested > MAX_LIMIT) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "LIMIT_INVALID",
                    "Số dòng mỗi trang phải từ 1 đến 50.");
        }
        return requested;
    }

    /** Takes rows fetched with {@code limit + 1}: the extra row proves another page exists. */
    public static <T> Page<T> of(List<T> rows, int limit, Function<T, Cursor> cursorOf) {
        if (rows.size() <= limit) {
            return new Page<>(List.copyOf(rows), null);
        }
        List<T> items = List.copyOf(rows.subList(0, limit));
        return new Page<>(items, cursorOf.apply(items.getLast()).encode());
    }
}
