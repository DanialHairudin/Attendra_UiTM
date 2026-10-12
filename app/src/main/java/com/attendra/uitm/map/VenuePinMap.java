package com.attendra.uitm.map;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import com.attendra.uitm.R;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

/**
 * The lecturer's venue map in the "Where" step.
 * Shows: the venue pin, the attendance radius circle and (optionally) the lecturer's own location.
 * The pin moves when the lecturer picks a search result, taps the map, or long-presses and drags the pin.
 */
public class VenuePinMap {

    public interface Listener { void onPinMoved(double lat, double lng); }

    private final MapView map;
    private final Marker pin;
    private final Polygon circle;
    private MyLocationNewOverlay myLocation;
    private double radiusM;
    private boolean editable = true;
    private Listener listener;

    public VenuePinMap(MapView map, double lat, double lng, double radiusM) {
        this.map = map;
        this.radiusM = radiusM;
        OsmSetup.styleMap(map);
        Context ctx = map.getContext();

        // Radius circle (gold, see-through).
        circle = new Polygon(map);
        int gold = ContextCompat.getColor(ctx, R.color.gold);
        circle.getFillPaint().setColor((gold & 0x00FFFFFF) | 0x33000000);  // 20% opacity fill
        circle.getOutlinePaint().setColor(gold);
        circle.getOutlinePaint().setStrokeWidth(4f);
        circle.setOnClickListener((poly, mv, pos) -> false);                 // let taps go through to the map

        // Venue pin. Long-press the pin to start dragging it.
        pin = new Marker(map);
        pin.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        pin.setDraggable(true);
        pin.setInfoWindow(null);
        pin.setIcon(ContextCompat.getDrawable(ctx, R.drawable.ic_map_pin));  // gold app pin
        pin.setOnMarkerClickListener((m, mv) -> true);                        // no popup, no auto-centre
        pin.setOnMarkerDragListener(new Marker.OnMarkerDragListener() {
            @Override public void onMarkerDrag(Marker m) { drawCircle(m.getPosition()); }
            @Override public void onMarkerDragEnd(Marker m) { notifyMoved(m.getPosition()); }
            @Override public void onMarkerDragStart(Marker m) { }
        });

        // Tapping anywhere on the map moves the pin there.
        MapEventsOverlay taps = new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (!editable) return false;
                setPin(p.getLatitude(), p.getLongitude(), false);
                return true;
            }
            @Override public boolean longPressHelper(GeoPoint p) { return false; }
        });

        map.getOverlays().add(0, taps);   // index 0 = receives taps last, after the pin
        map.getOverlays().add(circle);
        map.getOverlays().add(pin);
        map.getController().setZoom(17.0);
        setPin(lat, lng, false);
        map.getController().setCenter(new GeoPoint(lat, lng));
    }

    public void setListener(Listener l) { this.listener = l; }

    /**
     * Building mode (Class, Test, inside Club): the centre comes from /buildings, so the lecturer
     * cannot move the pin, only change the radius. Map pin mode (SULAM, outside Club): editable.
     */
    public void setEditable(boolean editable) {
        this.editable = editable;
        pin.setDraggable(editable);
    }

    /** Move the pin (from a search result, the map or "My location"). */
    public void setPin(double lat, double lng, boolean animate) {
        GeoPoint p = new GeoPoint(lat, lng);
        pin.setPosition(p);
        drawCircle(p);
        if (animate) map.getController().animateTo(p, 17.5, 600L);
        notifyMoved(p);
    }

    /** Called by the radius slider. */
    public void setRadius(double meters) {
        radiusM = meters;
        drawCircle(pin.getPosition());
    }

    public double getLat() { return pin.getPosition().getLatitude(); }
    public double getLng() { return pin.getPosition().getLongitude(); }
    public double getRadius() { return radiusM; }

    /**
     * Shows the lecturer's own location as a blue dot.
     * Call only after ACCESS_FINE_LOCATION has been granted. Returns false if it is not granted.
     */
    public boolean showMyLocation() {
        Context ctx = map.getContext();
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (myLocation == null) {
            myLocation = new MyLocationNewOverlay(new GpsMyLocationProvider(ctx), map);
            map.getOverlays().add(myLocation);
        }
        myLocation.enableMyLocation();
        map.invalidate();
        return true;
    }

    /** "Use my location" button: moves the pin to where the lecturer is standing now. */
    public void movePinToMyLocation(Runnable onNoFixYet) {
        if (myLocation == null && !showMyLocation()) { onNoFixYet.run(); return; }
        GeoPoint me = myLocation.getMyLocation();
        if (me != null) {
            setPin(me.getLatitude(), me.getLongitude(), true);
        } else {
            onNoFixYet.run();
            // Move as soon as the first GPS fix arrives (runs on a background thread, so post to the UI thread).
            myLocation.runOnFirstFix(() -> map.post(() -> {
                GeoPoint g = myLocation.getMyLocation();
                if (g != null) setPin(g.getLatitude(), g.getLongitude(), true);
            }));
        }
    }

    // ---- lifecycle: call from the Fragment ----
    public void onResume() { map.onResume(); if (myLocation != null) myLocation.enableMyLocation(); }
    public void onPause()  { if (myLocation != null) myLocation.disableMyLocation(); map.onPause(); }
    public void onDestroyView() { map.onDetach(); }

    private void drawCircle(GeoPoint centre) {
        circle.setPoints(Polygon.pointsAsCircle(centre, radiusM));
        map.invalidate();
    }

    private void notifyMoved(GeoPoint p) {
        map.invalidate();
        if (listener != null) listener.onPinMoved(p.getLatitude(), p.getLongitude());
    }
}
