package com.attendra.uitm.map;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;

import com.attendra.uitm.BuildConfig;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;

import java.io.File;

/**
 * One place for all OpenStreetMap (osmdroid) setup.
 * - init() is called once from the Application class.
 * - styleMap() is called for every MapView we show.
 */
public final class OsmSetup {

    /** UiTM Shah Alam (approximate). Used as the starting point of the map and to bias place search. */
    public static final double UITM_LAT = 3.0698;
    public static final double UITM_LNG = 101.5037;

    private OsmSetup() { }

    /** Call in Application.onCreate(), before any MapView is inflated. */
    public static void init(Context appContext) {
        IConfigurationProvider c = Configuration.getInstance();
        c.load(appContext, appContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
        // The OpenStreetMap tile servers require an identifying User-Agent.
        c.setUserAgentValue(BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME);
        // Keep the tile cache inside the app's own cache folder, so no storage permission is needed.
        File base = new File(appContext.getCacheDir(), "osmdroid");
        c.setOsmdroidBasePath(base);
        c.setOsmdroidTileCache(new File(base, "tiles"));
    }

    /** Common look and behaviour for every map in the app. */
    @SuppressLint("ClickableViewAccessibility")
    public static void styleMap(MapView map) {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);            // pinch to zoom
        map.setTilesScaledToDpi(true);              // readable street names on high-density screens
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setMinZoomLevel(5.0);
        map.setMaxZoomLevel(19.0);
        // OpenStreetMap data is free but must show the attribution.
        map.getOverlays().add(new CopyrightOverlay(map.getContext()));
        // When the map sits inside a ScrollView, stop the ScrollView from stealing map gestures.
        map.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            v.getParent().requestDisallowInterceptTouchEvent(a != MotionEvent.ACTION_UP && a != MotionEvent.ACTION_CANCEL);
            return false;
        });
    }
}
