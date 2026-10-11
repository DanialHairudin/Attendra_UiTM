package com.attendra.uitm.util;

import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Names for greetings and avatars (CLAUDE.md 12.6).
 *
 * Greetings use the preferred name the user picked ("Danial"), with the title
 * in front for lecturers ("Dr. Aina"). Until one is picked, the first suggestion
 * from NameSuggestions is used.
 */
public final class Names {

    /** Titles a lecturer can choose. "" means None. */
    public static final List<String> LECTURER_TITLES = Collections.unmodifiableList(
            Arrays.asList("Dr.", "Prof. Madya", "Prof.", "Encik", "Puan", "Cik", ""));

    /** Titles that can appear before a full name (lower case, without dots). */
    private static final Set<String> TITLES = new HashSet<>(Arrays.asList(
            "dr", "prof", "madya", "assoc", "professor", "ts", "ir", "encik", "en",
            "puan", "pn", "cik", "mr", "mrs", "ms", "miss", "sir", "dato", "datuk",
            "datin", "haji", "hajah", "hj", "hjh"));
    /** Joining words that are not part of the initials. */
    private static final Set<String> JOINERS = new HashSet<>(Arrays.asList(
            "bin", "binti", "bt", "bte", "b", "b.", "a/l", "a/p", "s/o", "d/o", "@"));

    private Names() {
    }

    /**
     * The name to greet someone with: "{title} {preferredName}", e.g. "Dr. Aina".
     * Falls back to the first suggestion from the full name.
     */
    public static String greetingName(String fullName, String preferredName, String title) {
        String name = preferredName == null || preferredName.trim().isEmpty()
                ? NameSuggestions.first(fullName) : preferredName.trim();
        return title == null || title.trim().isEmpty() ? name : title.trim() + " " + name;
    }

    /** greetingName() read straight from a lecturers/{uid} or students/{uid} snapshot. */
    public static String greetingName(DataSnapshot profile) {
        return greetingName(profile.child("name").getValue(String.class),
                profile.child("preferredName").getValue(String.class),
                profile.child("title").getValue(String.class));
    }

    /** Up to two capital letters from the real name words, e.g. "NA". */
    public static String initials(String fullName) {
        List<String> real = new ArrayList<>();
        for (String w : words(fullName)) {
            if (!isTitle(w) && !JOINERS.contains(w.toLowerCase(Locale.ROOT))) real.add(w);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < real.size() && sb.length() < 2; i++) {
            sb.append(Character.toUpperCase(real.get(i).charAt(0)));
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    private static String[] words(String s) {
        if (s == null || s.trim().isEmpty()) return new String[0];
        return s.trim().split("\\s+");
    }

    private static boolean isTitle(String word) {
        return TITLES.contains(word.toLowerCase(Locale.ROOT).replace(".", ""));
    }
}
