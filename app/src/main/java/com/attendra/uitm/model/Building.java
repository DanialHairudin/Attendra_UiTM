package com.attendra.uitm.model;

/**
 * One row of /buildings (CS1, CS2). Its lat/lng is the geofence centre for
 * Class and Test sessions. Seeded as 0,0 until Danny fills in the real values.
 */
public class Building {
    public String id;
    public String name;
    public double lat;
    public double lng;
    public long defaultRadius;

    public Building() {
    }

    /** False while lat/lng are still 0 (or missing): no session may use it yet. */
    public boolean hasLocation() {
        return lat != 0 && lng != 0;
    }
}
