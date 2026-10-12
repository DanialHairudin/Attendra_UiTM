package com.attendra.uitm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.attendra.uitm.util.GroupIds;

import org.junit.Test;

public class GroupIdsTest {

    @Test
    public void buildsCdcsIds() {
        assertEquals("CDCS2405A", GroupIds.build("CS240", 5, "A"));
        assertEquals("CDCS2306B", GroupIds.build("CS230", 6, "b"));
    }

    @Test
    public void convertsOldIdsOnly() {
        assertEquals("CDCS2405A", GroupIds.toCdcs("CS2405A"));
        assertEquals("CDCS2405A", GroupIds.toCdcs("CDCS2405A")); // safe to run twice
        assertEquals("CS240", GroupIds.toCdcs("CS240"));           // programme code
        assertEquals("CS240_ICT602", GroupIds.toCdcs("CS240_ICT602")); // course ID
    }

    @Test
    public void recognisesOldFormat() {
        assertTrue(GroupIds.isOldFormat("CS2664C"));
        assertFalse(GroupIds.isOldFormat("CDCS2664C"));
        assertFalse(GroupIds.isOldFormat(null));
    }
}
