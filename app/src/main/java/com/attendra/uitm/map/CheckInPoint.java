package com.attendra.uitm.map;

/** Where one student checked in (or tried to). */
public class CheckInPoint {
    public final String uid;
    public final String name;
    public final String status;   // "present", "late" or "rejected"
    public final double lat;
    public final double lng;
    public final double distanceM;
    public final long time;

    public CheckInPoint(String uid, String name, String status, double lat, double lng, double distanceM, long time) {
        this.uid = uid;
        this.name = name;
        this.status = status;
        this.lat = lat;
        this.lng = lng;
        this.distanceM = distanceM;
        this.time = time;
    }
}
