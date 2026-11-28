package com.tutorbridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The API table of `docs/context/r0a-scope.md`, for the access matrices of TB-23 to check they miss no row. */
public final class R0aScope {

    private static final Pattern ROW = Pattern.compile("^\\| ([^|]+) \\| \\d+ \\| (.+) \\|$");
    private static final Pattern ID = Pattern.compile("`(\\w+)`");

    private R0aScope() {}

    /** The operationIds of one group, as the table lists them. */
    public static Set<String> operationsOf(String group) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        for (String line : Files.readAllLines(Path.of("../docs/context/r0a-scope.md"))) {
            Matcher m = ROW.matcher(line);
            if (m.matches() && m.group(1).trim().equals(group)) {
                Matcher each = ID.matcher(m.group(2));
                while (each.find()) {
                    ids.add(each.group(1));
                }
            }
        }
        return ids;
    }
}
