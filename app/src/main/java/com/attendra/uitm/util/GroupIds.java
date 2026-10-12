package com.attendra.uitm.util;

import java.util.Locale;

/**
 * Class group IDs in the official UiTM format: "CD" + programme + part + letter,
 * e.g. programme CS240, part 5, group A → "CDCS2405A".
 *
 * Programme codes (CS240) and course IDs (CS240_ICT602) keep their own format;
 * only group IDs use the CD prefix.
 */
public final class GroupIds {

    private GroupIds() {
    }

    /** "CS240", 5, "A" → "CDCS2405A". */
    public static String build(String programme, int part, String letter) {
        return "CD" + programme + part + letter.toUpperCase(Locale.ROOT);
    }

    /** True for an ID in the old format, e.g. "CS2405A" (but not "CDCS2405A"). */
    public static boolean isOldFormat(String id) {
        return id != null && id.matches("CS\\d{4}[A-Z]");
    }

    /** "CS2405A" → "CDCS2405A". IDs already in the new format are returned unchanged. */
    public static String toCdcs(String id) {
        return isOldFormat(id) ? "CD" + id : id;
    }
}
