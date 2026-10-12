package com.attendra.uitm.map;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.attendra.uitm.R;
import com.google.firebase.database.DataSnapshot;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small map on the lecturer's Live Monitor page.
 * Shows the venue circle and one dot per student at the place where they checked in:
 * green = present, orange = late, red = rejected (for example outside the radius).
 * Tapping a student in the list calls focusStudent(uid) to fly the map to that dot.
 */
public class LiveCheckInMap {

    private final MapView map;
    private final Context ctx;
    private final Map<String, Marker> markers = new HashMap<>();
    private final List<GeoPoint> allPoints = new ArrayList<>();
    private final Polygon circle;
    private GeoPoint venue;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());

    public LiveCheckInMap(MapView map) {
        this.map = map;
        this.ctx = map.getContext();
        OsmSetup.styleMap(map);
        circle = new Polygon(map);
        int gold = ContextCompat.getColor(ctx, R.color.gold);
        circle.getFillPaint().setColor((gold & 0x00FFFFFF) | 0x26000000);
        circle.getOutlinePaint().setColor(gold);
        circle.getOutlinePaint().setStrokeWidth(4f);
        circle.setOnClickListener((poly, mv, pos) -> false);
        map.getOverlays().add(circle);
        map.getController().setZoom(17.0);
    }

    /** Draw the venue and its radius (from the session). */
    public void setVenue(double lat, double lng, double radiusM) {
        venue = new GeoPoint(lat, lng);
        circle.setPoints(Polygon.pointsAsCircle(venue, radiusM));
        map.getController().setCenter(venue);
        map.invalidate();
    }

    /** Replace all dots. Call every time the attendance or attempts listener fires. */
    public void setPoints(List<CheckInPoint> points) {
        for (Marker m : markers.values()) { m.closeInfoWindow(); map.getOverlays().remove(m); }
        markers.clear();
        allPoints.clear();
        for (CheckInPoint p : points) {
            Marker m = new Marker(map);
            GeoPoint g = new GeoPoint(p.lat, p.lng);
            m.setPosition(g);
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            m.setIcon(MapIcons.dot(ctx, MapIcons.statusColor(p.status)));
            m.setTitle(p.name);
            m.setSnippet(label(p.status) + " · " + timeFmt.format(new Date(p.time)));
            m.setSubDescription(Math.round(p.distanceM) + " m from the venue");
            map.getOverlays().add(m);
            markers.put(p.uid, m);
            allPoints.add(g);
        }
        map.invalidate();
    }

    /**
     * Fly to one student's check-in location and open the name bubble.
     * Returns false if this student has no location (not checked in yet, or marked manually).
     */
    public boolean focusStudent(String uid) {
        Marker m = markers.get(uid);
        if (m == null) return false;
        for (Marker other : markers.values()) other.closeInfoWindow();
        map.getController().animateTo(m.getPosition(), 18.5, 700L);
        m.showInfoWindow();
        return true;
    }

    /** Zoom out so the venue and every dot fit on screen. */
    public void showAll() {
        List<GeoPoint> pts = new ArrayList<>(allPoints);
        if (venue != null) pts.add(venue);
        if (pts.size() < 2) { if (venue != null) map.getController().animateTo(venue, 17.0, 500L); return; }
        BoundingBox box = BoundingBox.fromGeoPointsSafe(pts).increaseByScale(1.3f);
        map.post(() -> map.zoomToBoundingBox(box, true));
    }

    // ---- lifecycle ----
    public void onResume() { map.onResume(); }
    public void onPause() { map.onPause(); }
    public void onDestroyView() { map.onDetach(); }

    private static String label(String status) {
        if ("present".equals(status)) return "Present";
        if ("late".equals(status)) return "Late";
        return "Rejected";
    }

    /**
     * Builds the dots from Firebase.
     * attendance = snapshot of /attendance/{sessionId}
     * attempts   = snapshot of /attempts/{sessionId}
     * names      = uid -> display name (from the eligible student list the screen already loads)
     *
     * Rules: a GPS check-in shows its saved lat/lng. A manual record has no location, so it has no dot.
     * A student who only has rejected attempts shows the latest rejected attempt as a red dot.
     */
    public static List<CheckInPoint> fromSnapshots(DataSnapshot attendance, DataSnapshot attempts, Map<String, String> names) {
        Map<String, CheckInPoint> byUid = new HashMap<>();
        for (DataSnapshot s : attendance.getChildren()) {
            Double lat = s.child("lat").getValue(Double.class);
            Double lng = s.child("lng").getValue(Double.class);
            if (lat == null || lng == null) continue;                       // manual or absent: no location
            String status = s.child("status").getValue(String.class);
            if (!"present".equals(status) && !"late".equals(status)) continue;
            byUid.put(s.getKey(), new CheckInPoint(s.getKey(), nameOf(names, s.getKey()), status, lat, lng,
                    num(s.child("distance")), longOf(s.child("time"))));
        }
        for (DataSnapshot a : attempts.getChildren()) {
            String uid = a.child("uid").getValue(String.class);
            String result = a.child("result").getValue(String.class);
            Double lat = a.child("lat").getValue(Double.class);
            Double lng = a.child("lng").getValue(Double.class);
            if (uid == null || lat == null || lng == null) continue;
            if ("present".equals(result) || "late".equals(result) || "accepted".equals(result)) continue;
            CheckInPoint existing = byUid.get(uid);
            long t = longOf(a.child("time"));
            // Only show a rejected dot if the student has no accepted check-in, and keep the newest attempt.
            if (existing == null || ("rejected".equals(existing.status) && t > existing.time)) {
                byUid.put(uid, new CheckInPoint(uid, nameOf(names, uid), "rejected", lat, lng, num(a.child("distance")), t));
            }
        }
        return new ArrayList<>(byUid.values());
    }

    private static String nameOf(Map<String, String> names, String uid) {
        String n = names.get(uid);
        return n == null ? "Student" : n;
    }
    private static double num(DataSnapshot s) { Double d = s.getValue(Double.class); return d == null ? 0 : d; }
    private static long longOf(DataSnapshot s) { Long l = s.getValue(Long.class); return l == null ? 0 : l; }
}
