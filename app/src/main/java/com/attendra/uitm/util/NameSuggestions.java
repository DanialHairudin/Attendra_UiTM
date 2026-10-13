package com.attendra.uitm.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Suggests short names to call someone by, from their full registered name. */
public final class NameSuggestions {

    private static final Set<String> SEPARATORS = new HashSet<>(Arrays.asList(
            "bin", "binti", "bt", "bte", "b.", "a/l", "a/p", "s/o", "d/o", "@"));

    private static final Set<String> PREFIXES = new HashSet<>(Arrays.asList(
            "muhammad", "mohammad", "mohamad", "mohamed", "mohd", "muhd", "md", "nur",
            "nurul", "siti", "wan", "nik", "che", "tengku", "syed", "sharifah", "abang",
            "dayang"));

    /** Titles some people type into their full name; they are not part of the name. */
    private static final Set<String> TITLES = new HashSet<>(Arrays.asList(
            "dr", "prof", "madya", "assoc", "professor", "ts", "ir", "encik", "en", "puan",
            "pn", "cik", "mr", "mrs", "ms", "miss", "dato", "datuk", "datin", "tan sri",
            "haji", "hajah", "hj", "hjh"));

    private NameSuggestions() {
    }

    /** The suggestions, best first. */
    public static List<String> suggest(String fullName) {
        List<String> words = words(fullName);
        while (words.size() > 1 && isTitle(words.get(0))) words.remove(0);
        if (words.isEmpty()) return new ArrayList<>();

        // 3. Given name: the words before the first separator.
        List<String> given = new ArrayList<>();
        boolean hasSeparator = false;
        for (String w : words) {
            if (SEPARATORS.contains(w.toLowerCase(Locale.ROOT))) {
                hasSeparator = true;
                break;
            }
            given.add(w);
        }
        if (given.isEmpty()) given = words; // Name started with a separator: use everything

        Set<String> out = new LinkedHashSet<>();
        boolean startsWithPrefix = isPrefix(given.get(0));
        if (hasSeparator || startsWithPrefix || given.size() == 1) {
            // 4a. Malay-style: skip leading prefixes for the single-word suggestion.
            int first = 0;
            while (first < given.size() - 1 && isPrefix(given.get(first))) first++;
            out.add(given.get(first));
            out.add(join(given, 0, given.size()));
            out.add(given.get(0));
        } else {
            // 4b. No separator and no prefix: surname-first names.
            out.add(join(given, 1, given.size()));
            out.add(given.get(given.size() - 1));
            out.add(join(given, 0, given.size()));
            if (given.size() == 2) out.add(given.get(0)); // e.g. "Kavitha Ramasamy"
        }
        return new ArrayList<>(out);
    }

    /** The first suggestion, used when no preferred name has been chosen yet. */
    public static String first(String fullName) {
        List<String> s = suggest(fullName);
        return s.isEmpty() ? "" : s.get(0);
    }

    /** "2–20 letters and spaces" for the "Other" field. */
    public static String validOther(String typed) {
        if (typed == null) return null;
        String name = typed.trim().replaceAll("\\s+", " ");
        if (name.length() < 2 || name.length() > 20) return null;
        if (!name.matches("[\\p{L} ]+")) return null;
        return name;
    }

    // ----------------------------------------------------------------- helpers

    private static List<String> words(String s) {
        List<String> list = new ArrayList<>();
        if (s == null) return list;
        for (String w : s.trim().split("\\s+")) {
            if (!w.isEmpty()) list.add(tidyCase(w));
        }
        return list;
    }

    /** "DANIAL" or "danial" → "Danial". */
    private static String tidyCase(String w) {
        boolean allUpper = w.equals(w.toUpperCase(Locale.ROOT));
        boolean allLower = w.equals(w.toLowerCase(Locale.ROOT));
        if (!allUpper && !allLower) return w;
        if (SEPARATORS.contains(w.toLowerCase(Locale.ROOT))) return w; // Keep "A/P", "bin" as is
        String lower = w.toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }

    private static boolean isPrefix(String w) {
        return PREFIXES.contains(w.toLowerCase(Locale.ROOT));
    }

    private static boolean isTitle(String w) {
        return TITLES.contains(w.toLowerCase(Locale.ROOT).replace(".", ""));
    }

    private static String join(List<String> words, int from, int to) {
        return String.join(" ", words.subList(from, to));
    }
}
