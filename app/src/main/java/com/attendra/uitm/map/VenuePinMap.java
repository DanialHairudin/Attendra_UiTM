package com.attendra.uitm.map;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.attendra.uitm.R;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * Venue map: the venue pin, the attendance radius circle and (optionally) the user's own location.
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
        circle.setOnClickListener((poly, mv, pos) -> false);                 // Let taps go through to the map

        // Venue pin.
        pin = new Marker(map);
        pin.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        pin.setDraggable(true);
        pin.setInfoWindow(null);
        pin.setIcon(ContextCompat.getDrawable(ctx, R.drawable.ic_map_pin));  // Gold app pin
        pin.setOnMarkerClickListener((m, mv) -> true);                        // No popup, no auto-centre
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

        map.getOverlays().add(0, taps);   // Index 0 = receives taps last, after the pin
        map.getOverlays().add(circle);
        map.getOverlays().add(pin);
        map.getController().setZoom(17.0);
        setPin(lat, lng, false);
        map.getController().setCenter(new GeoPoint(lat, lng));
    }

    public void setListener(Listener l) { this.listener = l; }

    /**
     * Building mode (Class, Test, inside Club): the centre comes from /buildings, so the lecturer
     * cannot move the pin, only change the radius.
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

    /** Shows the lecturer's own location as a blue dot. */
    public boolean showMyLocation() {
        Context ctx = map.getContext();
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (myLocation == null) {
            myLocation = new MyLocationNewOverlay(new GpsMyLocationProvider(ctx), map);
            // A blue dot instead of osmdroid's default person icon / direction arrow.
            Bitmap dot = MapIcons.myLocationDot(ctx);
            myLocation.setPersonIcon(dot);
            myLocation.setDirectionIcon(dot);
            myLocation.setPersonAnchor(0.5f, 0.5f);
            myLocation.setDirectionAnchor(0.5f, 0.5f);
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
            // Move as soon as the first GPS fix arrives (runs on a background thread, so post to
            // the UI thread).
            myLocation.runOnFirstFix(() -> map.post(() -> {
                GeoPoint g = myLocation.getMyLocation();
                if (g != null) setPin(g.getLatitude(), g.getLongitude(), true);
            }));
        }
    }

    /** The blue dot's position, or null before showMyLocation() or before the first GPS fix. */
    @Nullable
    public GeoPoint getMyLocation() {
        return myLocation == null ? null : myLocation.getMyLocation();
    }

    /** Student map: zooms out so the whole radius circle and the blue dot fit. */
    public void zoomToVenueAndMe() {
        if (myLocation == null) return;
        if (myLocation.getMyLocation() != null) {
            fitVenueAnd(myLocation.getMyLocation());
        } else {
            // runOnFirstFix runs on a background thread, so post to the UI thread.
            myLocation.runOnFirstFix(() -> map.post(() -> {
                GeoPoint g = myLocation.getMyLocation();
                if (g != null) fitVenueAnd(g);
            }));
        }
    }

    private void fitVenueAnd(GeoPoint me) {
        List<GeoPoint> pts = new ArrayList<>(Polygon.pointsAsCircle(pin.getPosition(), radiusM));
        pts.add(me);
        BoundingBox box = BoundingBox.fromGeoPointsSafe(pts).increaseByScale(1.3f);
        map.post(() -> map.zoomToBoundingBox(box, true));
    }

    // ---- lifecycle: call from the Fragment / Activity
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
