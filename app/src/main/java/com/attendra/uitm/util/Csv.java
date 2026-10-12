package com.attendra.uitm.util;

/**
 * Builds a CSV file (RFC 4180) for the Session Report export (C11).
 *
 * A field is wrapped in double quotes when it contains a comma, a quote or a
 * line break, and quotes inside it are doubled: Ali "Bob" → "Ali ""Bob""".
 * Lines end with \r\n. The text starts with a UTF-8 byte order mark so
 * Excel shows names with special letters correctly.
 */
public final class Csv {

    private static final String BOM = "\uFEFF";

    private final StringBuilder out = new StringBuilder(BOM);

    /** Adds one line. null fields are written as empty. */
    public Csv row(String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) out.append(',');
            out.append(escape(fields[i]));
        }
        out.append("\r\n");
        return this;
    }

    @Override
    public String toString() {
        return out.toString();
    }

    public static String escape(String field) {
        if (field == null) return "";
        boolean quote = field.contains(",") || field.contains("\"")
                || field.contains("\n") || field.contains("\r");
        return quote ? "\"" + field.replace("\"", "\"\"") + "\"" : field;
    }

    /** A safe file name: letters, digits, dash and underscore only. */
    public static String fileName(String base) {
        String clean = base.replaceAll("[^A-Za-z0-9_-]+", "_").replaceAll("_+", "_");
        clean = clean.replaceAll("^_|_$", "");
        return (clean.isEmpty() ? "attendance" : clean) + ".csv";
    }
}
