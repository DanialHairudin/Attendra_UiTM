package com.attendra.uitm.map;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.attendra.uitm.R;
import com.attendra.uitm.util.Formats;
import com.google.firebase.database.DataSnapshot;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small map on the lecturer's Live Monitor page. */
public class LiveCheckInMap {

    private final MapView map;
    private final Context ctx;
    private final Map<String, Marker> markers = new HashMap<>();
    private final List<GeoPoint> allPoints = new ArrayList<>();
    private final Polygon circle;
    private GeoPoint venue;

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

    /** Replace all dots. */
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
            m.setSnippet(ctx.getString(label(p.status)) + " · " + Formats.time(p.time));
            m.setSubDescription(ctx.getString(R.string.live_map_distance, Math.round(p.distanceM)));
            map.getOverlays().add(m);
            markers.put(p.uid, m);
            allPoints.add(g);
        }
        map.invalidate();
    }

    /** Fly to one student's check-in location and open the name bubble. */
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

    // ---- lifecycle
    public void onResume() { map.onResume(); }
    public void onPause() { map.onPause(); }
    public void onDestroyView() { map.onDetach(); }

    private static int label(String status) {
        if ("present".equals(status)) return R.string.status_present;
        if ("late".equals(status)) return R.string.status_late;
        return R.string.status_rejected;
    }

    /** Builds the dots from Firebase. */
    public static List<CheckInPoint> fromSnapshots(DataSnapshot attendance, DataSnapshot attempts, Map<String, String> names) {
        Map<String, CheckInPoint> byUid = new HashMap<>();
        for (DataSnapshot s : attendance.getChildren()) {
            Double lat = s.child("lat").getValue(Double.class);
            Double lng = s.child("lng").getValue(Double.class);
            if (lat == null || lng == null) continue;                       // Manual or absent: no location
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
            // Only show a rejected dot if the student has no accepted check-in, and keep the newest
            // attempt.
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
