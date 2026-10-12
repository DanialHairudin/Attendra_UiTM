package com.attendra.uitm.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Check-in lat/lng are saved to 5 decimals (about 1 m), metres to 1 decimal. */
public class CheckInRoundingTest {

    @Test
    public void latLngRoundToFiveDecimals() {
        assertEquals(3.06981, CheckInRepository.round5(3.069812345), 0);
        assertEquals(101.50371, CheckInRepository.round5(101.503705), 0);
        assertEquals(-6.12346, CheckInRepository.round5(-6.123456), 0);
    }

    @Test
    public void metresRoundToOneDecimal() {
        assertEquals(18.4, CheckInRepository.round1(18.44), 0);
        assertEquals(214.0, CheckInRepository.round1(213.96), 0);
    }
}
