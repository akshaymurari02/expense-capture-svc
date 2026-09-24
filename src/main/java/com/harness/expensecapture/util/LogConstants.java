package com.harness.expensecapture.util;

/** 3-part logging constants: {@code [Service] [Message] | [Identifier]}. */
public final class LogConstants {

    public static final String SVC = "Expense Capture |";

    /** Single identifier, e.g. {@code | R-9f1c}. */
    public static final String ID_FMT = " | %s";

    /** Nested identifier, e.g. {@code | R-9f1c.T-3a7b}. */
    public static final String ID_FMT_NESTED = " | %s.%s";

    private LogConstants() {
        throw new AssertionError("No instances.");
    }

    public static String id(final String identifier) {
        return String.format(ID_FMT, identifier);
    }

    public static String id(final String parent, final String child) {
        return String.format(ID_FMT_NESTED, parent, child);
    }
}
