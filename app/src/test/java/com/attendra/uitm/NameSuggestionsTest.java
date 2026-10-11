package com.attendra.uitm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.attendra.uitm.util.NameSuggestions;

import org.junit.Test;

import java.util.Arrays;

/** The examples from CLAUDE.md 12.6, plus a few edge cases. */
public class NameSuggestionsTest {

    @Test
    public void malayNameWithBin() {
        assertEquals(Arrays.asList("Danial", "Muhammad Danial", "Muhammad"),
                NameSuggestions.suggest("Muhammad Danial Bin Mohd Hairudin"));
    }

    @Test
    public void chineseNameWithoutSeparator() {
        assertEquals(Arrays.asList("Wei Ming", "Ming", "Tan Wei Ming"),
                NameSuggestions.suggest("Tan Wei Ming"));
    }

    @Test
    public void indianNameWithAp() {
        assertEquals(Arrays.asList("Kavitha"), NameSuggestions.suggest("Kavitha A/P Ramasamy"));
    }

    @Test
    public void separatorsAreCaseInsensitive() {
        assertEquals("Aina", NameSuggestions.first("Nur Aina BINTI Ahmad"));
        assertEquals("Raju", NameSuggestions.first("Raju s/o Muthu"));
        assertEquals("Ali", NameSuggestions.first("Ali @ Alias Bin Abu"));
    }

    @Test
    public void allCapsIsTidied() {
        assertEquals("Danial", NameSuggestions.first("MUHAMMAD DANIAL BIN MOHD HAIRUDIN"));
    }

    @Test
    public void titleIsDropped() {
        assertEquals("Natrah", NameSuggestions.first("Prof. Madya Dr. Natrah binti Abdullah"));
    }

    @Test
    public void otherFieldRules() {
        assertEquals("Danny", NameSuggestions.validOther("  Danny "));
        assertEquals("Wei Ming", NameSuggestions.validOther("Wei  Ming"));
        assertNull(NameSuggestions.validOther("D"));
        assertNull(NameSuggestions.validOther("Danny123"));
        assertNull(NameSuggestions.validOther("A name that is far too long"));
    }
}
