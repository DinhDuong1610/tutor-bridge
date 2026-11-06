package com.tutorbridge.platform.api;

import java.util.regex.Pattern;

/** Strong ETags built from a version number. The client never builds one itself (G3-D07-06). */
public final class Etag {

    private static final Pattern STRONG = Pattern.compile("\"(0|[1-9][0-9]{0,17})\"");

    private Etag() {}

    /** The {@code ETag} response header for this version, for example {@code "3"}. */
    public static String of(long version) {
        if (version < 0) {
            throw new IllegalArgumentException("Version must not be negative: " + version);
        }
        return "\"" + version + "\"";
    }

    /** The version in an {@code If-Match} value. A weak, listed or malformed tag is stale (412). */
    public static long parse(String tag) {
        var matcher = STRONG.matcher(tag.strip());
        if (!matcher.matches()) {
            throw WritePrecondition.stale();
        }
        return Long.parseLong(matcher.group(1));
    }
}
