package com.attendra.uitm.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Short forms of a person's name for greetings and avatars.
 *
 * Malaysian names often have a title in front ("Prof. Madya Dr.") and a
 * patronymic ("bin", "binti"). Examples:
 *   "Prof. Madya Dr. Natrah binti Abdullah" → short "Dr. Natrah", first "Natrah", initials "NA"
 *   "Muhammad Danial bin Mohd Hairudin"     → short "Muhammad",   first "Muhammad", initials "MD"
 */
public final class Names {

    /** Titles that can appear before the name (compared in lower case, without dots). */
    private static final Set<String> TITLES = new HashSet<>(Arrays.asList(
            "dr", "prof", "madya", "assoc", "professor", "ts", "ir", "encik", "en",
            "puan", "pn", "cik", "mr", "mrs", "ms", "miss", "sir", "dato", "datuk",
            "datin", "tan", "sri", "haji", "hajah", "hj", "hjh"));
    /** Joining words that are not part of the initials. */
    private static final Set<String> JOINERS = new HashSet<>(Arrays.asList(
            "bin", "binti", "bt", "b", "a/l", "a/p", "al", "ap"));

    private Names() {
    }

    /** Lecturer greeting: the last title + the first real name, e.g. "Dr. Natrah". */
    public static String shortName(String fullName) {
        String[] words = words(fullName);
        String lastTitle = null;
        for (String w : words) {
            if (isTitle(w)) {
                lastTitle = w;
            } else {
                return lastTitle == null ? w : lastTitle + " " + w;
            }
        }
        return fullName == null ? "" : fullName.trim();
    }

    /** Student greeting: the first word that is not a title, e.g. "Danial". */
    public static String firstName(String fullName) {
        for (String w : words(fullName)) {
            if (!isTitle(w)) return w;
        }
        return fullName == null ? "" : fullName.trim();
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
