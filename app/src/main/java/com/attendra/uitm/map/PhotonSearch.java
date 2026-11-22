package com.attendra.uitm.map;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Search-as-you-type place search using Photon (https://photon.komoot.io), a free geocoder built on
 * OpenStreetMap data.
 */
public class PhotonSearch {

    public interface Callback {
        void onResults(List<PlaceResult> results);
        void onError(String message);
    }

    private static final String BASE = "https://photon.komoot.io/api/";
    private static final long DEBOUNCE_MS = 450;
    private static final int MIN_CHARS = 3;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String userAgent;
    private Runnable pending;
    private int latestRequest = 0; // Only touched on the main thread

    public PhotonSearch(String userAgent) {
        this.userAgent = userAgent;
    }

    /** Call from a TextWatcher on every change. */
    public void searchDebounced(String query, Callback cb) {
        if (pending != null) {
            main.removeCallbacks(pending);
        }
        String q;
        if (query == null) {
            q = "";
        } else {
            q = query.trim();
        }
        if (q.length() < MIN_CHARS) {
            latestRequest++;                       // Cancel any older answer
            cb.onResults(Collections.emptyList());
            return;
        }
        pending = new Runnable() {
            @Override
            public void run() {
                startSearch(q, cb);
            }
        };
        main.postDelayed(pending, DEBOUNCE_MS);
    }

    /** Stops searching when the page closes. */
    public void shutdown() {
        if (pending != null) {
            main.removeCallbacks(pending);
        }
        io.shutdownNow();
    }

    /** Asks the server on a background thread, then answers on the main thread. */
    private void startSearch(String q, Callback cb) {
        latestRequest++;
        final int id = latestRequest;
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    List<PlaceResult> list = fetch(q);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (id == latestRequest) {
                                cb.onResults(list);
                            }
                        }
                    });
                } catch (Exception e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (id == latestRequest) {
                                cb.onError("Search is not available. Check your internet connection.");
                            }
                        }
                    });
                }
            }
        });
    }

    /** Asks the Photon server and reads its answer. */
    private List<PlaceResult> fetch(String q) throws Exception {
        // lat/lon bias: results near UiTM Shah Alam come first.
        String url = BASE + "?q=" + URLEncoder.encode(q, "UTF-8")
                + "&limit=10&lat=" + OsmSetup.UITM_LAT + "&lon=" + OsmSetup.UITM_LNG;
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestProperty("User-Agent", userAgent);
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        try {
            if (con.getResponseCode() != 200) throw new Exception("HTTP " + con.getResponseCode());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                String line = r.readLine();
                while (line != null) {
                    sb.append(line);
                    line = r.readLine();
                }
            }
            return parse(sb.toString());
        } finally {
            con.disconnect();
        }
    }

    /** Photon returns GeoJSON. */
    static List<PlaceResult> parse(String json) throws Exception {
        List<PlaceResult> out = new ArrayList<>();
        JSONArray features = new JSONObject(json).optJSONArray("features");
        if (features == null) return out;
        for (int i = 0; i < features.length(); i++) {
            JSONObject f = features.getJSONObject(i);
            JSONObject p = f.optJSONObject("properties");
            JSONObject geometry = f.optJSONObject("geometry");
            JSONArray c;
            if (geometry == null) {
                c = null;
            } else {
                c = geometry.optJSONArray("coordinates");
            }
            if (p == null || c == null || c.length() < 2) continue;
            if (!"MY".equalsIgnoreCase(p.optString("countrycode", "MY"))) continue; // Malaysia only

            String name = p.optString("name", "");
            String street = p.optString("street", "");
            String house = p.optString("housenumber", "");
            // The place name, or the address if it has no name.
            String title = name;
            if (name.isEmpty()) {
                String address = street;
                if (!house.isEmpty()) {
                    address = house + " " + street;
                }
                title = address.trim();
            }
            if (title.isEmpty()) continue;

            // Subtitle: street, district, city, state without repeats.
            Set<String> parts = new LinkedHashSet<>();
            for (String k : new String[]{"street", "district", "locality", "city", "state"}) {
                String v = p.optString(k, "");
                if (!v.isEmpty() && !v.equalsIgnoreCase(title)) {
                    parts.add(v);
                }
            }
            out.add(new PlaceResult(title, String.join(", ", parts), c.getDouble(1), c.getDouble(0)));
            if (out.size() == 8) break;
        }
        return out;
    }
}
