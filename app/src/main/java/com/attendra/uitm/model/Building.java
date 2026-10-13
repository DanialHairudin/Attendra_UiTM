package com.attendra.uitm.model;

/** One row of /buildings (CS1, CS2). */
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
