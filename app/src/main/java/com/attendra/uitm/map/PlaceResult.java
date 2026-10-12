package com.attendra.uitm.map;

/** One suggestion returned by the place search. */
public class PlaceResult {
    public final String title;     // e.g. "i-City"
    public final String subtitle;  // e.g. "Seksyen 7, Shah Alam, Selangor"
    public final double lat;
    public final double lng;

    public PlaceResult(String title, String subtitle, double lat, double lng) {
        this.title = title;
        this.subtitle = subtitle;
        this.lat = lat;
        this.lng = lng;
    }

    /** Text saved in the session as the place name, e.g. "i-City, Seksyen 7, Shah Alam". */
    public String fullName() {
        return subtitle == null || subtitle.isEmpty() ? title : title + ", " + subtitle;
    }
}
