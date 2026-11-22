package com.attendra.uitm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.attendra.uitm.model.Attendance;

import org.junit.Test;

/** The 80% limit used by the semester summary and the attendance alert. */
public class AttendanceLimitTest {

    @Test
    public void exactly80IsNotBelow() {
        assertFalse(Attendance.belowLimit(8, 10));
    }

    @Test
    public void nineOfTwelveIsBelow() {
        assertTrue(Attendance.belowLimit(9, 12));
        assertEquals(75, Attendance.percent(9, 12));
    }

    @Test
    public void almost80IsBelowAndNeverShownAs80() {
        assertTrue(Attendance.belowLimit(39, 49)); // 79.59%
        assertEquals(79, Attendance.percent(39, 49));
    }

    @Test
    public void roundsLikeMyAttendance() {
        assertEquals(67, Attendance.percent(2, 3));
    }

    @Test
    public void noSessionsIsNotBelow() {
        assertFalse(Attendance.belowLimit(0, 0));
        assertEquals(0, Attendance.percent(0, 0));
    }
}
